"""Offline design-artifact validation. Requires jsonschema 4.x; never contacts services."""
from pathlib import Path
from copy import deepcopy
from datetime import datetime, timezone
import base64
import hashlib
import importlib.metadata
import json
import re

from jsonschema import Draft202012Validator, FormatChecker
from referencing import Registry, Resource

ROOT = Path(__file__).resolve().parents[1]
BASE = 'https://schemas.example.invalid/devops/jenkins-job-delete/v1/'
def load(name):
    return json.loads((ROOT / name).read_text(encoding='utf-8'))

schemas = {p.name: json.loads(p.read_text(encoding='utf-8')) for p in (ROOT / 'schemas').glob('*.json')}
registry = Registry().with_resources((s['$id'], Resource.from_contents(s)) for s in schemas.values())
formats = FormatChecker()
@formats.checks('date-time', raises=(ValueError, TypeError))
def date_time(value):
    if not isinstance(value, str):
        return True
    if not re.fullmatch(r'\d{4}-\d{2}-\d{2}[Tt]\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:[Zz]|[+-]\d{2}:\d{2})', value):
        return False
    return datetime.fromisoformat(value.upper().replace('Z', '+00:00')).tzinfo is not None

checks = []
def record(name, ok, detail=''):
    checks.append({'name': name, 'passed': bool(ok), 'detail': detail})
    if not ok:
        raise AssertionError(name + ': ' + detail)

for name, schema in schemas.items():
    Draft202012Validator.check_schema(schema)
    record('schema-valid:' + name, True)

def validator(schema_name, definition=None):
    if definition:
        s = {'$ref': BASE + schema_name + '#/$defs/' + definition}
    else:
        s = schemas[schema_name]
    return Draft202012Validator(s, registry=registry, format_checker=formats)

def check(name, data, schema_name, definition=None, valid=True):
    errors = list(validator(schema_name, definition).iter_errors(data))
    record(name, (len(errors) == 0) == valid,
           '; '.join(e.message for e in errors)[:600] if valid and errors else '')

solution = load('definitions/jenkins-job-delete.solution.json')
commands = load('definitions/commands.json')
binding = load('examples/connector-binding.template.json')
check('positive:solution', solution, 'solution-definition.schema.json')
for c in commands:
    check('positive:' + c['command_id'], c, 'command-definition.schema.json')
check('positive:draft-binding', binding, 'connector-binding.schema.json')
examples = {
    'target.json': 'Target', 'inspect-present.json': 'InspectResult',
    'inspect-absent.json': 'InspectResult', 'config-result.json': 'ConfigResult',
    'delete-ack.json': 'DeleteAcknowledgement', 'report-success.json': 'ExecutionReport',
    'report-unknown.json': 'ExecutionReport', 'intent-candidate.json': 'IntentCandidate'
}
for file, definition in examples.items():
    check('positive:' + file, load('examples/' + file), 'contracts.schema.json', definition)

negatives = []
def mutated(name, source, mutate, schema, definition=None):
    d = deepcopy(source)
    mutate(d)
    check('negative:' + name, d, schema, definition, valid=False)
    negatives.append({'name': name, 'schema': schema, 'definition': definition, 'input': d, 'expected_schema_valid': False})

mutated('delete-repeat', solution, lambda x: x['execution_policy'].update(max_delete_dispatches=2), 'solution-definition.schema.json')
mutated('fallback-enabled', solution, lambda x: x['execution_policy'].update(fallback_allowed=True), 'solution-definition.schema.json')
mutated('skip-backup-step', solution, lambda x: x['steps'].pop(2), 'solution-definition.schema.json')
mutated('swapped-steps', solution, lambda x: x['steps'].reverse(), 'solution-definition.schema.json')
mutated('normal-authorized', solution, lambda x: x['authorization'].update(required_role='NORMAL_USER'), 'solution-definition.schema.json')
mutated('unprotected-unknown', solution, lambda x: x['backup_policy'].update(protect_unknown_or_pending_human=False), 'solution-definition.schema.json')
mutated('raw-script', commands[2], lambda x: x.update(shell='rm -rf /'), 'command-definition.schema.json')
mutated('delete-as-query', commands[2], lambda x: x.update(command_type='QUERY'), 'command-definition.schema.json')
mutated('wildcard-target', load('examples/target.json'), lambda x: x.update(job_full_name='sandbox/*'), 'contracts.schema.json', 'Target')
mutated('partial-config', load('examples/config-result.json'), lambda x: x.update(complete=False), 'contracts.schema.json', 'ConfigResult')
mutated('partial-inspect', load('examples/inspect-present.json'), lambda x: x.update(scope_complete=False), 'contracts.schema.json', 'InspectResult')
mutated('present-null-count', load('examples/inspect-present.json'), lambda x: x.update(active_build_count=None), 'contracts.schema.json', 'InspectResult')
mutated('absence-fake-count', load('examples/inspect-absent.json'), lambda x: x.update(active_build_count=0), 'contracts.schema.json', 'InspectResult')
mutated('untrusted-absence', load('examples/inspect-absent.json'), lambda x: x.update(authoritative=False), 'contracts.schema.json', 'InspectResult')
mutated('invalid-time', load('examples/inspect-present.json'), lambda x: x.update(observed_at='yesterday'), 'contracts.schema.json', 'InspectResult')
mutated('success-without-backup', load('examples/report-success.json'), lambda x: x.update(backup_id=None), 'contracts.schema.json', 'ExecutionReport')
mutated('success-without-verify', load('examples/report-success.json'), lambda x: x.update(verification_evidence_ref=None), 'contracts.schema.json', 'ExecutionReport')
mutated('unknown-completed', load('examples/report-unknown.json'), lambda x: x.update(work_status='COMPLETED'), 'contracts.schema.json', 'ExecutionReport')
mutated('model-self-authorize', load('examples/intent-candidate.json'), lambda x: x.update(authorized=True), 'contracts.schema.json', 'IntentCandidate')

# These are artifact checks only, not a production semantic validator or workflow.
cmd_keys = {(c['command_id'], c['version']) for c in commands}
record('references:step-commands', all((s['command_ref']['command_id'], s['command_ref']['version']) in cmd_keys for s in solution['steps'] if s['kind']=='COMMAND'))
record('references:bounded-bindings', all(s['input_bindings'] == {'target':'plan.target'} for s in solution['steps'] if s['kind']=='COMMAND'))
record('references:backup-source', solution['steps'][2]['input_bindings'] == {'config':'steps.S20.output'})
record('policy:all-draft', solution['status']=='DRAFT' and all(c['status']=='DRAFT' for c in commands))
record('policy:binding-disabled', binding['enabled'] is False and binding['status']=='DRAFT')
record('policy:capability-coverage', {x['capability_id'] for x in binding['mappings']} == {c['capability_id'] for c in commands} and len(binding['mappings'])==3)
cfg = load('examples/config-result.json')
raw = base64.b64decode(cfg['content_base64'], validate=True)
record('fixture:byte-length', len(raw)==cfg['byte_length'])
record('fixture:sha256', hashlib.sha256(raw).hexdigest()==cfg['sha256'])
record('fixture:success-backup-hash', load('examples/report-success.json')['backup_sha256']==cfg['sha256'])
cases = load('tests/cases.json')
record('cases:unique-ids', len({c['case_id'] for c in cases}) == len(cases))
record('cases:12-mq-32-st', sum(c['module']=='model-quality-evaluation' for c in cases)==12 and sum(c['module']=='solution-tool-testing' for c in cases)==32)
record('cases:all-not-run', all(c['status']=='NOT_RUN' and c['actual_result'] is None for c in cases))

result = {
    'scope':'STATIC_ARTIFACT_VALIDATION_ONLY',
    'generated_at':datetime.now(timezone.utc).isoformat(),
    'jsonschema_version':importlib.metadata.version('jsonschema'),
    'schema_count':len(schemas), 'negative_fixture_count':len(negatives),
    'passed_checks':len(checks), 'failed_checks':0,
    'business_test_status':'NOT_RUN',
    'qwen_contacted':False,'mcp_contacted':False,'jenkins_contacted':False,
    'production_workflow_tested':False,'database_transactions_tested':False,
    'checks':checks
}
(ROOT/'tests/negative-schema-fixtures.json').write_text(json.dumps(negatives,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
(ROOT/'tests/static-validation-result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:v for k,v in result.items() if k!='checks'},ensure_ascii=False,indent=2))

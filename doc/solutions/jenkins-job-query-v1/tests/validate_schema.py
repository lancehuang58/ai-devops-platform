"""Offline schema/fixture checks; does not execute an Agent or contact any service."""
from pathlib import Path
from copy import deepcopy
from datetime import datetime, timezone
import json, importlib.metadata, re
from jsonschema import Draft202012Validator, FormatChecker
from referencing import Registry, Resource

P=Path(__file__).resolve().parents[1]
def load(n):return json.loads((P/n).read_text(encoding='utf-8'))
ss={p.name:json.loads(p.read_text()) for p in (P/'schemas').glob('*.json')}
reg=Registry().with_resources((s['$id'],Resource.from_contents(s)) for s in ss.values())
fc=FormatChecker()
@fc.checks('date-time',raises=(ValueError,TypeError))
def dt(v):
    if not isinstance(v,str):return True
    return bool(re.fullmatch(r'\d{4}-\d{2}-\d{2}[Tt]\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:[Zz]|[+-]\d{2}:\d{2})',v)) and datetime.fromisoformat(v.upper().replace('Z','+00:00')).tzinfo is not None
checks=[]
def record(name,ok):
    checks.append({'name':name,'passed':bool(ok)})
    assert ok,name
def validate(n,d,expected=True,name=None):
    es=list(Draft202012Validator(ss[n],registry=reg,format_checker=fc).iter_errors(d))
    record(name or n,(not es)==expected)
for n,s in ss.items():
    Draft202012Validator.check_schema(s);record('schema:'+n,True)
profile=load('definitions/job-query.solution.json');cmds=load('definitions/query-commands.json')
validate('query-solution.schema.json',profile,name='positive:profile')
for c in cmds:validate('query-command.schema.json',c,name='positive:'+c['command_id'])
rd=load('examples/read-jenkinsfile.json');ans=load('examples/answer.json')
validate('read-decision.schema.json',rd,name='positive:read')
validate('read-decision.schema.json',{'decision':'ANSWER','reason':'證據足夠','basis_evidence_refs':['EV-1']},name='positive:answer-decision')
validate('read-decision.schema.json',{'decision':'NEEDS_INPUT','question':'請指定 controller'},name='positive:needs-input')
validate('read-decision.schema.json',{'decision':'STOP','reason_code':'CAPABILITY_UNAVAILABLE','reason':'尚未接入 SCM'},name='positive:stop-decision')
validate('query-answer.schema.json',ans,name='positive:answer')
negative=[]
def bad(name,base,mut,schema):
    x=deepcopy(base);mut(x);validate(schema,x,False,'negative:'+name);negative.append({'name':name,'schema':schema,'input':x,'expected_schema_valid':False})
bad('change-mode',profile,lambda x:x.update(execution_mode='FIXED_CHANGE'),'query-solution.schema.json')
bad('requires-idle',profile,lambda x:x.update(requires_idle=True),'query-solution.schema.json')
bad('admin-only-query',profile,lambda x:x.update(allowed_roles=['ADMIN']),'query-solution.schema.json')
bad('fallback-enabled',profile,lambda x:x['stop_policy'].update(fallback_allowed=True),'query-solution.schema.json')
bad('zero-budget',profile,lambda x:x['budgets'].update(max_tool_calls=0),'query-solution.schema.json')
bad('change-command',cmds[0],lambda x:x.update(command_type='CHANGE'),'query-command.schema.json')
bad('side-effects',cmds[0],lambda x:x.update(external_business_side_effects=True),'query-command.schema.json')
bad('published-without-binding',cmds[0],lambda x:x.update(status='PUBLISHED'),'query-command.schema.json')
bad('retry',cmds[0],lambda x:x.update(retry_count=1),'query-command.schema.json')
bad('model-self-authorize',rd,lambda x:x.update(authorized=True),'read-decision.schema.json')
bad('arbitrary-shell',rd,lambda x:x['input']['selector'].update(shell='execute arbitrary script'),'read-decision.schema.json')
bad('missing-relation',rd,lambda x:x.pop('relation_ref'),'read-decision.schema.json')
bad('no-basis',rd,lambda x:x.update(basis_evidence_refs=[]),'read-decision.schema.json')
bad('no-citation',ans,lambda x:x['claims'][0].update(evidence_refs=[]),'query-answer.schema.json')
bad('complete-with-gap',ans,lambda x:x.update(unresolved_items=[{'topic':'部署','reason_code':'INSUFFICIENT_EVIDENCE'}]),'query-answer.schema.json')
bad('invalid-time',ans,lambda x:x.update(retrieved_at='today'),'query-answer.schema.json')
bad('partial-without-gap',ans,lambda x:x.update(answer_status='PARTIAL'),'query-answer.schema.json')
byid={c['command_id']:c for c in cmds}
record('references:entry-commands',all(x['command_id'] in byid for x in profile['entry_commands']))
for c in cmds:
    for k in ['input_schema_ref','output_schema_ref']:
        file,df=c[k].split('#/$defs/');record('reference:'+c['command_id']+':'+k,df in ss[file]['$defs'])
ref=byid[rd['command_id']]['input_schema_ref'];file,df=ref.split('#/$defs/')
v=Draft202012Validator({'$ref':ss[file]['$id']+'#/$defs/'+df},registry=reg,format_checker=fc)
record('read:per-command-input',v.is_valid(rd['input']))
wrong=deepcopy(rd['input']);wrong['selector']['build_number']=1
record('read:wrong-command-selector-rejected',not v.is_valid(wrong))
record('draft-only',profile['status']=='DRAFT' and all(c['status']=='DRAFT' for c in cmds))
record('bindings-disabled',load('examples/mcp-bindings.template.json')['enabled'] is False)
cases=load('tests/cases.json')
record('cases:12-mq-20-st',sum(c['case_id'].startswith('MQ-') for c in cases)==12 and sum(c['case_id'].startswith('ST-') for c in cases)==20)
record('cases:unique-ids',len({c['case_id'] for c in cases})==len(cases))
record('cases:not-run',all(c['actual_result'] is None and c['status']=='NOT_RUN' for c in cases))
result={'scope':'STATIC_ARTIFACT_VALIDATION_ONLY','generated_at':datetime.now(timezone.utc).isoformat(),'validator_version':importlib.metadata.version('jsonschema'),'schema_count':len(ss),'negative_fixture_count':len(negative),'passed_checks':len(checks),'failed_checks':0,'qwen_contacted':False,'mcp_contacted':False,'production_code_tested':False,'database_tested':False,'business_case_status':'NOT_RUN','checks':checks}
(P/'tests/static-validation-result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
(P/'tests/negative-schema-fixtures.json').write_text(json.dumps(negative,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items() if k!='checks'},ensure_ascii=False,indent=2))

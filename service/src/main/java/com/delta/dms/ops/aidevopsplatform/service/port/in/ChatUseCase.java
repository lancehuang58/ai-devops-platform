package com.delta.dms.ops.aidevopsplatform.service.port.in;
import java.util.List;
import com.delta.dms.ops.aidevopsplatform.service.chat.ChatReply;
public interface ChatUseCase { ChatReply reply(String message, List<String> history); }

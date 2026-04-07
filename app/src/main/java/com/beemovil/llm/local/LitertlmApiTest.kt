package com.beemovil.llm.local

import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents

fun testApi(conv: Conversation) {
    val list = listOf(Content.Text("test"))
    conv.sendMessage(Message.of(list))
    // we also try Contents.builder()
}

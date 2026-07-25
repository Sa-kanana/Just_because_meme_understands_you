"""用 LangChain create_tool_calling_agent 构建搜梗智能体。"""

from __future__ import annotations

from langchain.agents import AgentExecutor, create_tool_calling_agent
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_core.tools import BaseTool

from app.agents.prompts import SYSTEM_PROMPT

AGENT_SYSTEM = (
    SYSTEM_PROMPT
    + "\n\n你是工具增强智能体：回答前必须调用 search_meme_knowledge 获取检索片段；"
    "只能依据工具返回内容作答；对用户只写可读正文，不要输出 meme_id 或其他内部字段。"
)


def build_meme_search_agent(
    llm: BaseChatModel,
    tools: list[BaseTool],
    *,
    max_iterations: int = 3,
) -> AgentExecutor:
    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", AGENT_SYSTEM),
            MessagesPlaceholder("chat_history", optional=True),
            ("human", "{input}"),
            MessagesPlaceholder("agent_scratchpad"),
        ]
    )
    agent = create_tool_calling_agent(llm, tools, prompt)
    return AgentExecutor(
        agent=agent,
        tools=tools,
        verbose=False,
        max_iterations=max_iterations,
        handle_parsing_errors=True,
        return_intermediate_steps=False,
    )

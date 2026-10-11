"""用 LangChain create_tool_calling_agent 构建搜梗智能体。"""

from __future__ import annotations

from langchain.agents import AgentExecutor, create_tool_calling_agent
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_core.tools import BaseTool

from app.agents.prompts import AGENT_SYSTEM_PROMPT

AGENT_TOOL_ALLOWLIST = frozenset(
    {
        "search_meme_knowledge",
        "search_admin_knowledge",
    }
)


def build_meme_search_agent(
    llm: BaseChatModel,
    tools: list[BaseTool],
    *,
    max_iterations: int = 3,
) -> AgentExecutor:
    supplied_names = [tool.name for tool in tools]
    unknown_names = sorted(set(supplied_names) - AGENT_TOOL_ALLOWLIST)
    if unknown_names:
        raise ValueError(f"tools outside the fixed allowlist: {', '.join(unknown_names)}")
    if len(supplied_names) != len(set(supplied_names)):
        raise ValueError("duplicate tool names are not allowed")

    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", AGENT_SYSTEM_PROMPT),
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

import pytest

from app.agents.query_intent import greeting_reply


@pytest.mark.parametrize(
    ("query", "expected_reply"),
    [
        ("你好！", "你好！"),
        ("谢谢", "不客气！"),
        ("在吗？", "我在。"),
        ("你好是什么梗？", None),
        ("不明觉厉是什么意思？", None),
    ],
)
def test_greeting_detection_only_matches_standalone_social_messages(query, expected_reply):
    assert greeting_reply(query) == expected_reply

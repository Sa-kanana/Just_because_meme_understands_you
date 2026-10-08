import pytest

from app.core.settings import Settings
from app.retrieval import embeddings


async def test_embed_text_sends_plain_text_as_a_singleton_batch(monkeypatch):
    calls = []

    class FakeEmbeddings:
        async def aembed_documents(self, texts):
            calls.append(texts)
            return [[0.1, 0.2]]

        async def aembed_query(self, _text):
            raise AssertionError("query token-array path should not be used")

    monkeypatch.setattr(embeddings, "_get_embeddings", lambda _settings: FakeEmbeddings())
    settings = Settings(internal_api_key="test-key", openai_api_key="test-key", vector_embedding_dim=2)

    vector = await embeddings.embed_text("  a   meme query  ", settings)

    assert vector == [0.1, 0.2]
    assert calls == [["a meme query"]]


def test_openai_embeddings_keeps_text_input_compatible_with_provider(monkeypatch):
    captured = {}

    class FakeOpenAIEmbeddings:
        def __init__(self, **kwargs):
            captured.update(kwargs)

    monkeypatch.setattr(embeddings, "_embeddings", None)
    monkeypatch.setattr(embeddings, "OpenAIEmbeddings", FakeOpenAIEmbeddings)
    settings = Settings(internal_api_key="test-key", openai_api_key="test-key")

    embeddings._get_embeddings(settings)

    assert captured["check_embedding_ctx_length"] is False


async def test_embed_text_does_not_store_a_local_vector_after_provider_failure(monkeypatch):
    class FailingEmbeddings:
        async def aembed_documents(self, _texts):
            raise TimeoutError("provider unavailable")

    monkeypatch.setattr(embeddings, "_get_embeddings", lambda _settings: FailingEmbeddings())
    settings = Settings(internal_api_key="test-key", openai_api_key="test-key", vector_embedding_dim=2)

    with pytest.raises(RuntimeError, match="Embedding API request failed"):
        await embeddings.embed_text("query", settings)


async def test_embed_texts_does_not_store_local_vectors_after_provider_failure(monkeypatch):
    class FailingEmbeddings:
        async def aembed_documents(self, _texts):
            raise TimeoutError("provider unavailable")

    monkeypatch.setattr(embeddings, "_get_embeddings", lambda _settings: FailingEmbeddings())
    settings = Settings(internal_api_key="test-key", openai_api_key="test-key", vector_embedding_dim=2)

    with pytest.raises(RuntimeError, match="Embedding API request failed"):
        await embeddings.embed_texts(["chunk"], settings)

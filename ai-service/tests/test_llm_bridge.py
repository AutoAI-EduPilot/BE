"""LlmBridge protocol and profile configuration tests."""

import pytest
from pydantic import BaseModel, SecretStr, ValidationError

from edupilot_ai.llm.bridge import LlmBridge
from edupilot_ai.settings import AgentLlmProfile, ReasoningEffort, Settings
from tests.fakes import FakeLlm


class ExampleResponse(BaseModel):
    answer: str


def accepts_bridge(_bridge: LlmBridge) -> None:
    """Static type assertion used by mypy."""


async def test_fake_llm_satisfies_protocol_and_uses_profile(settings: Settings) -> None:
    expected = ExampleResponse(answer="stub")
    fake = FakeLlm([expected])
    accepts_bridge(fake)

    profile = settings.agent_llm_profile
    result = await fake.complete_json(
        messages=[{"role": "user", "content": "test"}],
        response_model=ExampleResponse,
        profile=profile,
        timeout_seconds=12.5,
    )

    assert result.output is expected
    assert result.usage.model == "grok-4.5"
    assert fake.timeouts == [12.5]
    assert profile == AgentLlmProfile(
        model="grok-4.5",
        reasoning_effort=ReasoningEffort.MEDIUM,
        max_tokens=16_384,
        temperature=None,
    )
    assert profile.model_dump(by_alias=True) == {
        "model": "grok-4.5",
        "reasoningEffort": "medium",
        "maxTokens": 16_384,
        "temperature": None,
    }


def test_role_profiles_apply_independent_token_overrides() -> None:
    settings = Settings(
        _env_file=None,
        edupilot_internal_token=SecretStr("contract-test-token"),
        xai_api_key=SecretStr("xai-test-not-real"),
        model_name="grok-4.5",
        agent_max_tokens=16_384,
        orchestrator_max_tokens=4096,
        qa_max_tokens=4096,
        quiz_max_tokens=12_288,
    )

    assert settings.orchestrator_llm_profile == AgentLlmProfile(
        model="grok-4.5",
        reasoning_effort=ReasoningEffort.LOW,
        max_tokens=4096,
    )
    assert settings.qa_llm_profile == AgentLlmProfile(
        model="grok-4.5",
        reasoning_effort=ReasoningEffort.LOW,
        max_tokens=4096,
    )
    assert settings.quiz_llm_profile == AgentLlmProfile(
        model="grok-4.5",
        reasoning_effort=ReasoningEffort.MEDIUM,
        max_tokens=12_288,
    )
    assert settings.note_llm_profile == AgentLlmProfile(
        model="grok-4.5",
        reasoning_effort=ReasoningEffort.MEDIUM,
        max_tokens=16_384,
    )


def test_role_profiles_fall_back_to_global_model_and_token_budget(
    settings: Settings,
) -> None:
    assert settings.orchestrator_llm_profile.max_tokens == settings.agent_max_tokens
    assert settings.qa_llm_profile.max_tokens == settings.agent_max_tokens
    assert settings.quiz_llm_profile.max_tokens == settings.agent_max_tokens


@pytest.mark.parametrize(
    "environment_name",
    [
        "ORCHESTRATOR_MAX_TOKENS",
        "QA_MAX_TOKENS",
        "QUIZ_MAX_TOKENS",
    ],
)
def test_role_token_overrides_reject_zero(
    environment_name: str,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv(environment_name, "0")
    with pytest.raises(ValidationError):
        Settings(
            _env_file=None,
            edupilot_internal_token=SecretStr("contract-test-token"),
            xai_api_key=SecretStr("xai-test-not-real"),
        )

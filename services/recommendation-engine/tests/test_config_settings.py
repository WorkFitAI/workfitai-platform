"""
Tests for app/config.py (Settings helpers + load_vault_config).

get_settings() is already exercised indirectly by every route test via
conftest's `client` fixture; this file targets the parts that aren't:
get_kafka_topics/max_resume_size_bytes/is_production, and load_vault_config's
enabled/disabled/exception branches (patched at the hvac boundary — VAULT_ENABLED
is forced "false" in conftest.py, so these branches need direct invocation).
"""

from unittest.mock import patch

from app.config import Settings, load_vault_config


class TestSettingsHelpers:
    def test_get_kafka_topics_returns_all_four_topics_in_order(self):
        settings = Settings(
            KAFKA_TOPIC_JOB_CREATED="created",
            KAFKA_TOPIC_JOB_UPDATED="updated",
            KAFKA_TOPIC_JOB_DELETED="deleted",
            KAFKA_TOPIC_JOB_EXPIRED="expired",
        )
        assert settings.get_kafka_topics() == ["created", "updated", "deleted", "expired"]

    def test_max_resume_size_bytes_converts_mb(self):
        settings = Settings(MAX_RESUME_SIZE_MB=5)
        assert settings.max_resume_size_bytes == 5 * 1024 * 1024

    def test_is_production_true_case_insensitive(self):
        settings = Settings(ENVIRONMENT="PRODUCTION")
        assert settings.is_production() is True

    def test_is_production_false_for_other_environments(self):
        settings = Settings(ENVIRONMENT="development")
        assert settings.is_production() is False


class TestLoadVaultConfig:
    def test_disabled_returns_empty_dict(self, monkeypatch):
        monkeypatch.setenv("VAULT_ENABLED", "false")
        assert load_vault_config() == {}

    def test_enabled_but_connect_fails_returns_empty(self, monkeypatch):
        monkeypatch.setenv("VAULT_ENABLED", "true")
        with patch("app.vault_client.load_config_from_vault", return_value={}):
            assert load_vault_config() == {}

    def test_enabled_sets_env_vars_from_vault_config(self, monkeypatch):
        monkeypatch.setenv("VAULT_ENABLED", "true")
        monkeypatch.delenv("SOME_VAULT_KEY", raising=False)
        with patch(
            "app.vault_client.load_config_from_vault",
            return_value={"SOME_VAULT_KEY": "vault-value"},
        ):
            result = load_vault_config()
        assert result == {"SOME_VAULT_KEY": "vault-value"}
        import os

        assert os.environ["SOME_VAULT_KEY"] == "vault-value"
        del os.environ["SOME_VAULT_KEY"]

    def test_does_not_override_existing_env_var(self, monkeypatch):
        monkeypatch.setenv("VAULT_ENABLED", "true")
        monkeypatch.setenv("EXISTING_KEY", "original")
        with patch(
            "app.vault_client.load_config_from_vault",
            return_value={"EXISTING_KEY": "from-vault"},
        ):
            load_vault_config()
        import os

        assert os.environ["EXISTING_KEY"] == "original"

    def test_import_error_is_caught_and_returns_empty(self, monkeypatch):
        monkeypatch.setenv("VAULT_ENABLED", "true")
        with patch("app.vault_client.load_config_from_vault", side_effect=RuntimeError("boom")):
            assert load_vault_config() == {}

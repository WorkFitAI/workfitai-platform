"""
Tests for app/vault_client.py (VaultClient, load_config_from_vault).

hvac.Client is mocked at the constructor boundary — no real Vault server.
"""

from unittest.mock import MagicMock, patch

import hvac
import pytest

from app.vault_client import VaultClient, load_config_from_vault


class TestConnect:
    def test_success(self):
        fake_hvac = MagicMock()
        fake_hvac.is_authenticated.return_value = True
        with patch("app.vault_client.hvac.Client", return_value=fake_hvac):
            client = VaultClient(vault_addr="http://vault:8200", vault_token="tok")
            assert client.connect() is True
            assert client.client is fake_hvac

    def test_authentication_failure_returns_false(self):
        fake_hvac = MagicMock()
        fake_hvac.is_authenticated.return_value = False
        with patch("app.vault_client.hvac.Client", return_value=fake_hvac):
            client = VaultClient()
            assert client.connect() is False

    def test_exception_returns_false(self):
        with patch("app.vault_client.hvac.Client", side_effect=RuntimeError("no route")):
            client = VaultClient()
            assert client.connect() is False

    def test_defaults_from_env(self, monkeypatch):
        monkeypatch.setenv("VAULT_ADDR", "http://env-vault:8200")
        monkeypatch.setenv("VAULT_TOKEN", "env-token")
        client = VaultClient()
        assert client.vault_addr == "http://env-vault:8200"
        assert client.vault_token == "env-token"


class TestGetSecrets:
    def test_connects_lazily_when_not_connected(self):
        client = VaultClient()
        with patch.object(client, "connect", return_value=False) as connect_mock:
            secrets = client.get_secrets()
        connect_mock.assert_called_once()
        assert secrets == {}

    def test_returns_secrets_from_kv_v2(self):
        client = VaultClient()
        fake_hvac = MagicMock()
        fake_hvac.secrets.kv.v2.read_secret_version.return_value = {
            "data": {"data": {"model.path": "/models/x"}}
        }
        client.client = fake_hvac
        secrets = client.get_secrets()
        assert secrets == {"model.path": "/models/x"}
        assert client._secrets_cache == secrets

    def test_invalid_path_returns_empty(self):
        client = VaultClient()
        fake_hvac = MagicMock()
        fake_hvac.secrets.kv.v2.read_secret_version.side_effect = hvac.exceptions.InvalidPath()
        client.client = fake_hvac
        assert client.get_secrets() == {}

    def test_unexpected_error_returns_empty(self):
        client = VaultClient()
        fake_hvac = MagicMock()
        fake_hvac.secrets.kv.v2.read_secret_version.side_effect = RuntimeError("boom")
        client.client = fake_hvac
        assert client.get_secrets() == {}

    def test_custom_path_used_when_provided(self):
        client = VaultClient()
        fake_hvac = MagicMock()
        fake_hvac.secrets.kv.v2.read_secret_version.return_value = {"data": {"data": {}}}
        client.client = fake_hvac
        client.get_secrets(path="custom/path")
        call_kwargs = fake_hvac.secrets.kv.v2.read_secret_version.call_args.kwargs
        assert call_kwargs["path"] == "custom/path"


class TestGetSecret:
    def test_returns_value_when_cached(self):
        client = VaultClient()
        client._secrets_cache = {"key": "value"}
        assert client.get_secret("key") == "value"

    def test_returns_default_when_missing(self):
        client = VaultClient()
        client._secrets_cache = {"other": "value"}
        assert client.get_secret("key", default="fallback") == "fallback"

    def test_fetches_secrets_when_cache_empty(self):
        client = VaultClient()

        def _populate_cache():
            client._secrets_cache = {"key": "fetched"}
            return client._secrets_cache

        with patch.object(client, "get_secrets", side_effect=_populate_cache) as mock:
            result = client.get_secret("key")
        mock.assert_called_once()
        assert result == "fetched"


class TestRefreshSecrets:
    def test_clears_cache_then_refetches(self):
        client = VaultClient()
        client._secrets_cache = {"stale": "data"}
        with patch.object(client, "get_secrets", return_value={"fresh": "data"}) as mock:
            result = client.refresh_secrets()
        mock.assert_called_once()
        assert result == {"fresh": "data"}


class TestIsConnected:
    def test_false_when_no_client(self):
        client = VaultClient()
        assert client.is_connected is False

    def test_true_when_client_authenticated(self):
        client = VaultClient()
        fake_hvac = MagicMock()
        fake_hvac.is_authenticated.return_value = True
        client.client = fake_hvac
        assert client.is_connected is True


class TestLoadConfigFromVault:
    def test_connect_failure_returns_empty(self):
        with patch("app.vault_client.VaultClient.connect", return_value=False):
            assert load_config_from_vault() == {}

    def test_success_maps_known_keys_and_filters_none(self):
        with patch("app.vault_client.VaultClient.connect", return_value=True), \
             patch(
                 "app.vault_client.VaultClient.get_secrets",
                 return_value={
                     "model.path": "/models/x",
                     "batch.size": 16,
                     "kafka.bootstrap.servers": "kafka:9092",
                 },
             ):
            config = load_config_from_vault()
        assert config["MODEL_PATH"] == "/models/x"
        assert config["BATCH_SIZE"] == 16
        assert config["KAFKA_BOOTSTRAP_SERVERS"] == "kafka:9092"
        # Keys with no matching secret must be filtered out (None-valued).
        assert "FAISS_INDEX_PATH" not in config

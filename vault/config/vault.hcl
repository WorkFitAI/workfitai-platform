storage "file" {
  path = "/vault/data"
}

listener "tcp" {
  address     = "0.0.0.0:8200"
  tls_disable = 1  # Tắt TLS (HTTP)
}

disable_mlock = true
ui = true

# Notas do projeto

## Versao client/servidor
- A cada mudanca grande, subir a versao nos DOIS lugares, com o mesmo valor:
  `servidor.py::SERVER_VERSION` e `rede/ServerConfig.java::CLIENT_VERSION`
  (ex: v0.2 -> v0.3).
- O client manda a versao em `/check_version` ao apertar Play; se nao bater, o
  servidor responde 426 e o client mostra "Version outdated, please update your game".

package com.teste.game.rede;

/**
 * Mesmo endereco usado hoje pelo cliente Godot (ver Global.gd::server_url) -
 * o servidor Python/Flask/SocketIO em ./server nao muda com a migracao, os
 * dois clientes falam com a mesma instancia.
 */
public class ServerConfig {
    public static final String BASE_URL = "http://192.168.18.124:3000";
    public static final String CLIENT_VERSION = "v0.25"; // igual servidor.py::SERVER_VERSION
}

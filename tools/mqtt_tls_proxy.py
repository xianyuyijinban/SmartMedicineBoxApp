#!/usr/bin/env python3
import argparse
import socket
import ssl
import threading
import time


def pump(src: socket.socket, dst: socket.socket, tag: str) -> None:
    try:
        while True:
            data = src.recv(4096)
            if not data:
                break
            if tag.endswith("c->s"):
                msg_type = (data[0] >> 4) if len(data) > 0 else 0
                mqtt_names = {
                    1: "CONNECT",
                    3: "PUBLISH",
                    8: "SUBSCRIBE",
                    12: "PINGREQ",
                    14: "DISCONNECT",
                }
                name = mqtt_names.get(msg_type, f"T{msg_type}")
                print(f"[{time.strftime('%H:%M:%S')}] {tag} {name} len={len(data)}", flush=True)
            dst.sendall(data)
    except Exception:
        pass
    finally:
        try:
            dst.shutdown(socket.SHUT_WR)
        except Exception:
            pass
        try:
            src.shutdown(socket.SHUT_RD)
        except Exception:
            pass
        print(f"[{time.strftime('%H:%M:%S')}] {tag} closed", flush=True)


def handle_client(client: socket.socket, remote_host: str, remote_port: int, insecure: bool) -> None:
    peer = None
    try:
        peer = f"{client.getpeername()[0]}:{client.getpeername()[1]}"
    except Exception:
        peer = "unknown"

    print(f"[{time.strftime('%H:%M:%S')}] inbound {peer}", flush=True)
    try:
        client.settimeout(None)
        raw = socket.create_connection((remote_host, remote_port), timeout=10.0)
        raw.settimeout(None)
        ctx = ssl.create_default_context()
        if insecure:
            ctx.check_hostname = False
            ctx.verify_mode = ssl.CERT_NONE
        remote = ctx.wrap_socket(raw, server_hostname=remote_host)
        remote.settimeout(None)

        t1 = threading.Thread(target=pump, args=(client, remote, f"{peer} c->s"), daemon=True)
        t2 = threading.Thread(target=pump, args=(remote, client, f"{peer} s->c"), daemon=True)
        t1.start()
        t2.start()
        t1.join()
        t2.join()
    except Exception as e:
        print(f"[{time.strftime('%H:%M:%S')}] {peer} error: {e}", flush=True)
    finally:
        try:
            client.close()
        except Exception:
            pass


def main() -> None:
    parser = argparse.ArgumentParser(description="Plain MQTT to TLS MQTT proxy")
    parser.add_argument("--listen-host", default="0.0.0.0")
    parser.add_argument("--listen-port", type=int, default=1883)
    parser.add_argument("--remote-host", required=True)
    parser.add_argument("--remote-port", type=int, default=8883)
    parser.add_argument("--insecure", action="store_true", help="Disable TLS cert verification")
    args = parser.parse_args()

    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind((args.listen_host, args.listen_port))
    srv.listen(8)
    print(
        f"[{time.strftime('%H:%M:%S')}] proxy {args.listen_host}:{args.listen_port} "
        f"-> tls://{args.remote_host}:{args.remote_port} (insecure={args.insecure})"
    , flush=True)

    while True:
        client, _ = srv.accept()
        threading.Thread(
            target=handle_client,
            args=(client, args.remote_host, args.remote_port, args.insecure),
            daemon=True,
        ).start()


if __name__ == "__main__":
    main()

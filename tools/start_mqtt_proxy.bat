@echo off
setlocal
cd /d %~dp0
python mqtt_tls_proxy.py --listen-host 0.0.0.0 --listen-port 1883 --remote-host jaf12a6c.ala.cn-hangzhou.emqxsl.cn --remote-port 8883 --insecure
endlocal

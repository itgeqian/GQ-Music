@echo off
echo Starting Elasticsearch...


:: 启动 Redis
echo Starting Redis...
start /D "D:\Redis-8.2.1-Windows-x64-msys2" redis-server.exe  

:: 启动 Nacos
echo Starting Nacos...
start /D "D:\nacos\bin" startup.cmd -m standalone

:: 启动 MinIO
echo Starting MinIO...
cd /d "D:\minio2024" && start minio.RELEASE.2024-09-13T20-26-02Z server ./data

:: 保持窗口打开，查看服务日志
pause

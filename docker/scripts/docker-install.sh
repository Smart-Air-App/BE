#!/bin/bash

# Docker 설치 확인 및 설치
if ! command -v docker &> /dev/null; then
    echo "Docker가 설치되어 있지 않습니다. 설치를 시작합니다..."
    sudo apt-get update
    sudo apt-get install -y \
        apt-transport-https \
        ca-certificates \
        curl \
        gnupg \
        lsb-release
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
    echo \
        "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu \
        $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
    sudo apt-get update
    sudo apt-get install -y docker-ce docker-ce-cli containerd.io
    sudo usermod -aG docker $USER
    echo "Docker 설치 완료"
else
    echo "Docker가 이미 설치되어 있습니다."
fi

# Docker Compose 설치 확인 및 설치
if ! command -v docker-compose &> /dev/null; then
    echo "Docker Compose가 설치되어 있지 않습니다. 설치를 시작합니다..."
    sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    sudo chmod +x /usr/local/bin/docker-compose
    echo "Docker Compose 설치 완료"
else
    echo "Docker Compose가 이미 설치되어 있습니다."
fi

# Docker 서비스 시작
sudo systemctl start docker
sudo systemctl enable docker

echo "Docker 설정 완료" 
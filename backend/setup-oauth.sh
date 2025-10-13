#!/bin/bash

# Google OAuth 2.0 환경 변수 설정 스크립트
# 사용법: ./setup-oauth.sh YOUR_CLIENT_SECRET

if [ $# -ne 1 ]; then
    echo "사용법: $0 <GOOGLE_CLIENT_SECRET>"
    echo ""
    echo "Google Cloud Console에서 클라이언트 시크릿을 확인한 후"
    echo "클라이언트 시크릿을 이 스크립트에 전달하세요."
    echo ""
    echo "예시:"
    echo "  $0 GOCSPX-abcdef123456"
    exit 1
fi

export GOOGLE_CLIENT_ID="944868309517-t06evs8nf92dh9nq5ucbp6b69naq265i.apps.googleusercontent.com"
export GOOGLE_CLIENT_SECRET="$1"

echo "Google OAuth 환경 변수가 설정되었습니다:"
echo "GOOGLE_CLIENT_ID: $GOOGLE_CLIENT_ID"
echo "GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET:0:10}..."
echo ""
echo "이제 Spring Boot 애플리케이션을 실행하세요:"
echo "  ./mvnw spring-boot:run"

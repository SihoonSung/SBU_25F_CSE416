#!/bin/bash

# Google OAuth2 Setup Script for SAM System
# This script helps you set up the Google OAuth2 client secret

echo "🔧 Google OAuth2 Setup for SAM System"
echo "======================================"
echo ""

# Check if .env file exists
if [ ! -f ".env" ]; then
    echo "📝 Creating .env file from template..."
    cp env.example .env
    echo "✅ .env file created!"
    echo ""
fi

echo "📋 Next steps:"
echo "1. Go to Google Cloud Console: https://console.cloud.google.com/apis/credentials"
echo "2. Find your OAuth 2.0 Client ID: 944868309517-t06evs8nf92dh9nq5ucbp6b69naq265i.apps.googleusercontent.com"
echo "3. Click on it and copy the 'Client secret' value"
echo "4. Edit the .env file and replace 'your_actual_client_secret_here' with your actual client secret"
echo "5. Save the file"
echo ""

echo "🚀 After setting up the client secret, you can run the application with:"
echo "   ./mvnw spring-boot:run"
echo ""

echo "⚠️  IMPORTANT: Never commit the .env file to version control!"
echo "   The .env file is already in .gitignore"
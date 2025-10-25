import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import './Login.css'

/**
 * Login page component with Google OAuth2 integration.
 * Handles OAuth callback processing and user authentication flow.
 * 
 * Note: Google OAuth2 integration and authentication flow were implemented 
 * with assistance from Cursor AI and ChatGPT for proper OAuth handling 
 * and secure authentication patterns.
 */
function Login() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [isLoading, setIsLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const backendBase = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080'
  const googleUrl = `${backendBase}/oauth2/authorization/google`
  
  // OAuth callback handling
  useEffect(() => {
    const success = searchParams.get('success')
    const error = searchParams.get('error')
    const message = searchParams.get('message')
    
    if (error === 'user_not_found') {
      setErrorMessage(message || 'Your account is not registered in the system. Please contact administrator.')
      return
    }
    
    if (error) {
      console.error('OAuth error:', error)
      setErrorMessage('Login failed. Please try again.')
      return
    }
    
    if (success === 'true') {
      setIsLoading(true)
      // Redirect to home page after successful login
      navigate('/home', { replace: true })
      setIsLoading(false)
    }
  }, [searchParams, navigate])

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-header">
          <h1 className="sbu-title">SBU Academics Management</h1>
          <h2 className="sbu-subtitle">Stony Brook University Academics Management</h2>
        </div>
        
        <div className="login-form">
          {/* Error Message Display */}
          {errorMessage && (
            <div className="error-message">
              <div className="error-icon">⚠️</div>
              <div className="error-content">
                <h3>Access Denied</h3>
                <p>{errorMessage}</p>
                <button 
                  className="retry-button" 
                  onClick={() => {
                    setErrorMessage('')
                    window.location.href = '/login'
                  }}
                >
                  Try Again
                </button>
              </div>
            </div>
          )}
          
          {isLoading ? (
            <div className="loading-container">
              <div className="loading-spinner"></div>
                  <p>Logging in...</p>
            </div>
          ) : (
            <a className="google-button" href={googleUrl}>
              <svg className="google-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48">
                <path fill="#FFC107" d="M43.611,20.083H42V20H24v8h11.303c-1.649,4.657-6.08,8-11.303,8c-6.627,0-12-5.373-12-12 s5.373-12,12-12c3.059,0,5.842,1.156,7.961,3.039l5.657-5.657C34.046,6.053,29.268,4,24,4C12.955,4,4,12.955,4,24 s8.955,20,20,20s20-8.955,20-20C44,22.659,43.862,21.35,43.611,20.083z"/>
                <path fill="#FF3D00" d="M6.306,14.691l6.571,4.818C14.655,16.108,18.961,14,24,14c3.059,0,5.842,1.156,7.961,3.039l5.657-5.657 C34.046,6.053,29.268,4,24,4C16.318,4,9.656,8.337,6.306,14.691z"/>
                <path fill="#4CAF50" d="M24,44c5.166,0,9.86-1.977,13.409-5.192l-6.191-5.238C29.211,35.091,26.715,36,24,36 c-5.202,0-9.619-3.317-11.277-7.951l-6.54,5.036C9.5,39.556,16.227,44,24,44z"/>
                <path fill="#1976D2" d="M43.611,20.083H42V20H24v8h11.303c-0.793,2.24-2.231,4.166-4.086,5.571 c0.001-0.001,0.002-0.001,0.003-0.002l6.191,5.238C36.767,40.19,44,36,44,24C44,22.659,43.862,21.35,43.611,20.083z"/>
              </svg>
              Login with SBU Google Account
            </a>
          )}
        </div>
        
        <div className="login-footer">
          <div className="sbu-logo">
            <svg width="40" height="40" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect width="40" height="40" rx="8" fill="#B91C1C"/>
              <path d="M20 8L24 16H32L26 22L28 30L20 26L12 30L14 22L8 16H16L20 8Z" fill="white"/>
            </svg>
          </div>
        </div>
      </div>
    </div>
  )
}

export default Login

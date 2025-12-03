/*
 * TP: Used Cursor to implement login page with Google OAuth2 integration
 */

import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import './Login.css'
import sbuLogo from '../assets/SBU- LOGO.png'
import LoadingModal from '../components/LoadingModal'

/**
 * Login page component with Google OAuth2 integration.
 * Handles OAuth callback processing and user authentication flow.
 */
function Login() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [isLoading, setIsLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const backendBase = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080'
  const googleUrl = `${backendBase}/oauth2/authorization/google`

  // Bypass mode state
  const BYPASS_MODE_ENABLED = true // Enabled for demo purposes
  const [email, setEmail] = useState('')
  const [isBypassLoading, setIsBypassLoading] = useState(false)

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

  // Bypass login function
  const handleBypassLogin = async () => {
    if (!email.trim()) {
      setErrorMessage('Please enter email address')
      return
    }

    setIsBypassLoading(true)
    setErrorMessage('')

    try {
      const response = await fetch(`${backendBase}/api/auth/bypass-login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        credentials: 'include',
        body: JSON.stringify({ email: email.trim() }),
      })

      const data = await response.json()

      if (data.success) {
        // Login successful - redirect to home
        navigate('/home', { replace: true })
      } else {
        setErrorMessage(data.error || 'Login failed. User may not be imported via YAML.')
      }
    } catch (error) {
      console.error('Bypass login error:', error)
      setErrorMessage('Login failed. Please try again.')
    } finally {
      setIsBypassLoading(false)
    }
  }

  return (
    <div className="login-page">
      {/* Error Message Display - Only show error box when there's an error */}
      {errorMessage ? (
        <div className="error-box">
          <div className="error-content">
            <h3>Access Denied</h3>
            <p>{errorMessage}</p>
            <button
              className="retry-button"
              onClick={() => {
                setErrorMessage('')
                // Clear URL parameters and reload login page
                window.location.href = '/login'
              }}
            >
              Try Again
            </button>
          </div>
        </div>
      ) : (
        /* Login Card - Only show when there's no error */
        <div className="login-card">
          <div className="login-header">
            <h1 className="sbu-title">SBU Academics Management</h1>
            <h2 className="sbu-subtitle">Stony Brook University Academics Management</h2>
          </div>

          <div className="login-form">
            <a className="google-button" href={googleUrl}>
              <svg className="google-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48">
                <path fill="#FFC107" d="M43.611,20.083H42V20H24v8h11.303c-1.649,4.657-6.08,8-11.303,8c-6.627,0-12-5.373-12-12 s5.373-12,12-12c3.059,0,5.842,1.156,7.961,3.039l5.657-5.657C34.046,6.053,29.268,4,24,4C12.955,4,4,12.955,4,24 s8.955,20,20,20s20-8.955,20-20C44,22.659,43.862,21.35,43.611,20.083z" />
                <path fill="#FF3D00" d="M6.306,14.691l6.571,4.818C14.655,16.108,18.961,14,24,14c3.059,0,5.842,1.156,7.961,3.039l5.657-5.657 C34.046,6.053,29.268,4,24,4C16.318,4,9.656,8.337,6.306,14.691z" />
                <path fill="#4CAF50" d="M24,44c5.166,0,9.86-1.977,13.409-5.192l-6.191-5.238C29.211,35.091,26.715,36,24,36 c-5.202,0-9.619-3.317-11.277-7.951l-6.54,5.036C9.5,39.556,16.227,44,24,44z" />
                <path fill="#1976D2" d="M43.611,20.083H42V20H24v8h11.303c-0.793,2.24-2.231,4.166-4.086,5.571 c0.001-0.001,0.002-0.001,0.003-0.002l6.191,5.238C36.767,40.19,44,36,44,24C44,22.659,43.862,21.35,43.611,20.083z" />
              </svg>
              Login with SBU Google Account
            </a>
          </div>

          <div className="login-footer">
            <div className="sbu-logo">
              <img src={sbuLogo} alt="Stony Brook University Logo" className="sbu-logo-img" />
            </div>
          </div>

          {/* Bypass Mode Login Section */}
          {BYPASS_MODE_ENABLED && (
            <div className="bypass-login-section" style={{
              marginTop: '2rem',
              marginLeft: '-40px',
              marginRight: '-40px',
              padding: '1.5rem',
              border: '2px dashed #ff9800',
              borderRadius: '8px',
              backgroundColor: '#fff3e0',
              boxSizing: 'border-box'
            }}>
              <h3 style={{ marginTop: 0, color: '#e65100', fontSize: '1.1rem' }}>
                Bypass Mode Login (Demo/Test Only)
              </h3>

              <input
                type="email"
                placeholder="Enter email address (from YAML import)"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                style={{
                  width: '100%',
                  boxSizing: 'border-box',
                  padding: '0.75rem',
                  marginBottom: '1rem',
                  border: '1px solid #ddd',
                  borderRadius: '4px',
                  fontSize: '1rem'
                }}
                onKeyPress={(e) => {
                  if (e.key === 'Enter') {
                    handleBypassLogin()
                  }
                }}
              />

              <button
                onClick={handleBypassLogin}
                disabled={isBypassLoading}
                style={{
                  width: '100%',
                  padding: '0.75rem',
                  backgroundColor: '#ff9800',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  fontSize: '1rem',
                  fontWeight: 'bold',
                  cursor: isBypassLoading ? 'not-allowed' : 'pointer',
                  opacity: isBypassLoading ? 0.6 : 1
                }}
              >
                {isBypassLoading ? 'Logging in...' : 'Login (Bypass Mode)'}
              </button>

              <p className="bypass-notice" style={{
                marginTop: '1rem',
                fontSize: '0.85rem',
                color: '#666',
                fontStyle: 'italic'
              }}>
                Note: User must be imported via YAML before logging in with bypass mode.
              </p>
            </div>
          )}
        </div>
      )}
      <LoadingModal isOpen={isLoading || isBypassLoading} message="Logging in..." />
    </div>
  )
}

export default Login

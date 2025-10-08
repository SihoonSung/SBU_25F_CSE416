import {useEffect, useState } from 'react'
import { Routes, Route, Navigate, useLocation } from 'react-router-dom'
import './App.css'
import NavigationBar from './components/NavigationBar'
import Dashboard from './pages/Dashboard'
import Login from './pages/Login'
import { isAuthenticated } from './utils/auth'

function App() {
  const [title, setTitle] = useState('Default value')

  useEffect(() => {
    fetch('http://localhost:8080/home')
      .then((response) => response.text())
      .then((text) => setTitle(text))
      .catch((error) => console.error('Error fetching ', error))
  }, [])

  // Created Router path for each page
  const location = useLocation()
  const isLoginRoute = location.pathname === '/login'

  return (
    <div>
      {!isLoginRoute && <NavigationBar />}
      <main className="main-content">
        {!isLoginRoute && <h1>React + {title}</h1>}
        <Routes>
          <Route path="/login" element={<Login/>} />
          <Route path="/" element={isAuthenticated() ? <Dashboard/> : <Navigate to="/login" replace />} />
          <Route path="/home" element={isAuthenticated() ? <Dashboard/> : <Navigate to="/login" replace />} />

          
        </Routes>
      </main>
    </div>
  )
}

export default App

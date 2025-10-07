import {useEffect, useState } from 'react'
import { Routes, Route } from 'react-router-dom'
import './App.css'
import NavigationBar from './components/NavigationBar'
import Dashboard from './pages/Dashboard'

function App() {
  const [title, setTitle] = useState('Default value')

  useEffect(() => {
    fetch('http://localhost:8080/home')
      .then((response) => response.text())
      .then((text) => setTitle(text))
      .catch((error) => console.error('Error fetching ', error))
  }, [])

  // Created Router path for each page
  return (
    <div>
      <NavigationBar />
      <main className="main-content">
        <h1>React + {title}</h1>
        <Routes>
          <Route path="/" element= {<Dashboard/>} />
          <Route path="/home" element={<Dashboard/>} />

          
        </Routes>
      </main>
    </div>
  )
}

export default App

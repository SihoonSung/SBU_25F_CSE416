import {useEffect, useState } from 'react'
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

  return (
    <div>
      <NavigationBar />
      <main className="main-content">
        <h1>React + {title}</h1>
        <Dashboard />
      </main>
    </div>
  )
}

export default App

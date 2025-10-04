import {useEffect, useState } from 'react'
import './App.css'

function App() {
  const [title, setTitle] = useState('Default value')

  useEffect(() => {
    fetch('http://localhost:8080/home')
      .then((response) => response.text())
      .then((text) => setTitle(text))
      .catch((error) => console.error('Error fetching ', error))
  }, [])

  return (
    <>
      <h1>React + {title}</h1>
    </>
  )
}

export default App

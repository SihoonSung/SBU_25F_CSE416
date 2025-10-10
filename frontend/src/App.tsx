import { Routes, Route, Navigate, useLocation } from 'react-router-dom'
import './App.css'
import NavigationBar from './components/NavigationBar'
import Dashboard from './pages/Dashboard'
import Login from './pages/Login'
import CourseSearch from './pages/CourseSearch'
import CourseRegistration from './pages/CourseRegistration'
import { isAuthenticated } from './utils/auth'

function App() {

  // Created Router path for each page
  const location = useLocation()
  const isLoginRoute = location.pathname === '/login'

  return (
    <div>
      {!isLoginRoute && <NavigationBar />}
      <main className="main-content">
        <Routes>
          <Route path="/login" element={<Login/>} />
          <Route path="/" element={isAuthenticated() ? <Dashboard/> : <Navigate to="/login" replace />} />
          {/* <Route path="/home" element={isAuthenticated() ? <Dashboard/> : <Navigate to="/login" replace />} /> */} {/* temporary removed to test */}
          <Route path="/home" element={<Dashboard/>} />
          <Route path="/course-search" element={<CourseSearch/>} />
          <Route path="/course-registration" element={<CourseRegistration/>} />
          <Route path="/registration" element={<CourseRegistration/>} />
        </Routes>
      </main>
    </div>
  )
}

export default App

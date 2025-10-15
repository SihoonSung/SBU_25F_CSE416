import { Routes, Route, Navigate, useLocation } from 'react-router-dom'
import './App.css'
import NavigationBar from './components/NavigationBar'
import Dashboard from './pages/Dashboard'
import Login from './pages/Login'
import Profile from './pages/Profile'
import CourseSearch from './pages/CourseSearch'
import CourseRegistration from './pages/CourseRegistration'
import UserManagement from './pages/UserManagement'
import PdfUpload from './pages/PdfUpload'
import CourseCatalogManagement from './pages/CourseCatalogManagement'
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
          <Route path="/home" element={isAuthenticated() ? <Dashboard/> : <Navigate to="/login" replace />} />
          <Route path="/course-search" element={<CourseSearch/>} />
          <Route path="/course-registration" element={<CourseRegistration/>} />
          <Route path="/registration" element={<CourseRegistration/>} />
          <Route path="/profile" element={<Profile/>}/>
          <Route path="/user-management" element={<UserManagement/>} />
          <Route path="/pdf-upload" element={<PdfUpload/>} />
          <Route path="/course-catalog-management" element={<CourseCatalogManagement/>} />
        </Routes>
      </main>
    </div>
  )
}

export default App

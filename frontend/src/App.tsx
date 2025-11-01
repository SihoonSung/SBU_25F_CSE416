import { Routes, Route, useLocation } from 'react-router-dom'
import './App.css'
import NavigationBar from './components/NavigationBar'
import ProtectedRoute from './components/ProtectedRoute'
import Planner from './pages/Planner'
import Dashboard from './pages/Dashboard'
import Login from './pages/Login'
import Profile from './pages/Profile'
import CourseSearch from './pages/CourseSearch'
import CourseRegistration from './pages/CourseRegistration'
import UserManagement from './pages/UserManagement'
import DataImport from './pages/DataImport'
import DegreeRequirements from './pages/DegreeRequirements'
import DegreeAudit from './pages/DegreeAudit'
import MajorMinorDeclaration from './pages/MajorMinorDeclaration'
import AdvisorApproval from './pages/AdvisorApproval'
import PdfUpload from './pages/PdfUpload'
import Roster from './pages/Roster'
import Grades from './pages/Grades'

function App() {
  const location = useLocation()
  const isLoginRoute = location.pathname === '/login'

  return (
    <div>
      {!isLoginRoute && <NavigationBar />}
      <main className="main-content">
        <Routes>
          <Route path="/login" element={<Login/>} />
          <Route path="/" element={<ProtectedRoute><Dashboard/></ProtectedRoute>} />
          <Route path="/home" element={<ProtectedRoute><Dashboard/></ProtectedRoute>} />
          <Route path="/course-search" element={<ProtectedRoute><CourseSearch/></ProtectedRoute>} />
          <Route path="/course-registration" element={<ProtectedRoute><CourseRegistration/></ProtectedRoute>} />
          <Route path="/planner" element={<ProtectedRoute><Planner/></ProtectedRoute>} />
          <Route path="/registration" element={<ProtectedRoute><CourseRegistration/></ProtectedRoute>} />
          <Route path="/profile" element={<ProtectedRoute><Profile/></ProtectedRoute>}/>
          <Route path="/user-management" element={<ProtectedRoute><UserManagement/></ProtectedRoute>} />
          <Route path="/data-import" element={<ProtectedRoute><DataImport/></ProtectedRoute>} />
          <Route path="/degree-requirements" element={<ProtectedRoute><DegreeRequirements/></ProtectedRoute>} />
          <Route path="/degree-audit" element={<ProtectedRoute><DegreeAudit/></ProtectedRoute>} />
          <Route path="/major-minor-declaration" element={<ProtectedRoute><MajorMinorDeclaration/></ProtectedRoute>} />
          <Route path="/advisor-approval" element={<ProtectedRoute><AdvisorApproval/></ProtectedRoute>} />
          <Route path="/pdf-upload" element={<ProtectedRoute><PdfUpload/></ProtectedRoute>} />
          <Route path="/roster" element={<ProtectedRoute><Roster/></ProtectedRoute>} />
          <Route path="/grades" element={<ProtectedRoute><Grades/></ProtectedRoute>} />
        </Routes>
      </main>
    </div>
  )
}

export default App

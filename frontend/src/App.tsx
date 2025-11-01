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
import PdfUpload from './pages/PdfUpload'
import CourseCatalogManagement from './pages/CourseCatalogManagement'
import DegreeRequirementsImport from './pages/DegreeRequirementsImport'
import DegreeRequirements from './pages/DegreeRequirements'
import DegreeAudit from './pages/DegreeAudit'
import MajorMinorDeclaration from './pages/MajorMinorDeclaration'
import AdvisorApproval from './pages/AdvisorApproval'

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
          <Route path="/pdf-upload" element={<ProtectedRoute><PdfUpload/></ProtectedRoute>} />
          <Route path="/course-catalog-management" element={<ProtectedRoute><CourseCatalogManagement/></ProtectedRoute>} />
          <Route path="/degree-requirements-import" element={<ProtectedRoute><DegreeRequirementsImport/></ProtectedRoute>} />
          <Route path="/degree-requirements" element={<ProtectedRoute><DegreeRequirements/></ProtectedRoute>} />
          <Route path="/degree-audit" element={<ProtectedRoute><DegreeAudit/></ProtectedRoute>} />
          <Route path="/major-minor-declaration" element={<ProtectedRoute><MajorMinorDeclaration/></ProtectedRoute>} />
          <Route path="/advisor-approval" element={<ProtectedRoute><AdvisorApproval/></ProtectedRoute>} />
        </Routes>
      </main>
    </div>
  )
}

export default App

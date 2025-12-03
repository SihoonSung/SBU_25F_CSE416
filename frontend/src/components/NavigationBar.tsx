/*
 * TP: Used Cursor to implement navigation bar component with authentication and routing
 */

import { useEffect, useState } from 'react';
import './NavigationBar.css';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { getCurrentUser, isAuthenticated as checkAuth, signOut } from '../utils/auth';


function NavigationBar() {
  const navigate = useNavigate();
  const location = useLocation();

  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [role, setRole] = useState<string | null>(null);
  const [authenticated, setAuthenticated] = useState(false);

  useEffect(() => {
    let mounted = true;
    const fetchRole = async () => {
      try {
        const authed = await checkAuth();
        if (!mounted) return;
        setAuthenticated(authed);
        if (authed) {
          const me = await getCurrentUser();
          if (!mounted) return;
          const backendRole = me.user?.role ?? '';
          // Map backend roles to navbar keys
          const mapped = backendRole.toUpperCase();
          switch (mapped) {
            case 'STUDENT':
              setRole('student');
              break;
            case 'INSTRUCTOR':
              setRole('professor');
              break;
            case 'ADVISOR':
              setRole('academic_advisor');
              break;
            case 'REGISTRAR':
            case 'ADMIN':
              setRole('registrar');
              break;
            default:
              setRole('student');
          }
        } else {
          setRole(null);
        }
      } catch (error) {
        console.error('Error fetching user role:', error);
        if (!mounted) return;
        setAuthenticated(false);
        setRole(null);
      }
    };
    
    fetchRole();
    return () => { mounted = false; };
  }, [location.pathname]); // Re-fetch role when route changes

  //Role based navbar menus
   const navbar_menus : Record<string, string[]>  = {
    "student" : ["Course Search", "Registration", "Planner", "Degree Audit", "Sign out"],
    "professor" :["Course Search", "Registration Overrides", "Roaster", "Grades", "Sign out"],
    "academic_advisor" : ["Advisor Approval", "Degree Audit", "Sign out"],
    "registrar" : ["User Management", "Data Import", "Degree Requirements", "Class Management","Registration Schedule", "Sign out"]

  }

  const urlConverter = (text: string) => {
    const overrides: Record<string, string> = {
      'Registration Overrides': '/advisor-approval',
      'Advisor Approval': '/advisor-approval',
      'Registration Management': '/course-registration',
      'Degree Requirements': '/degree-requirements',
      'Degree Audit': '/degree-audit',
      'Data Import': '/data-import',
      'Class Management': '/pdf-upload',
      'Roaster': '/roster',
      'Grades': '/grades',
      'Registration Schedule': '/admin/registration-schedules',
    };
    return overrides[text] ?? ('/' + text.toLowerCase().replace(/ /g, '-'));
  };
  return (
    <nav className="navbar">
      <div className="navbar-content">
        {/* Logo Section */}
        <div className="navbar-logo">
          <Link to = "/home">
          <div className="logo-main">SAM</div>
          <div className="logo-subtitle">
            <div className="university-name">Stony Brook University</div>
            <div className="system-name">Academics Management</div>
          </div>
          </Link>
        </div>

        {/* TP : Used Cursor making Hamburger Menu Toggle */}
        <button 
          className="menu-toggle" 
          onClick={() => setIsMenuOpen(!isMenuOpen)}
          aria-label="Toggle navigation"
        >
          <span className={`hamburger-icon ${isMenuOpen ? 'is-open' : ''}`}></span>
        </button>

    
        {/* TP : Used Cursor making menus depend on roles */}
        <div className={`navbar-links ${isMenuOpen ? 'is-open' : ''}`}>
          {role && navbar_menus[role]?.map((Item : string, idx : number) => {
            const path = urlConverter(Item);
            if (Item === 'Sign out') {
              return authenticated ? (
                <button key={idx} className="nav-link" onClick={() => { signOut(); navigate('/login'); }}>
                  Sign out
                </button>
              ) : (
                <Link key={idx} to="/login" className="nav-link">Sign in</Link>
              );
            }
            return (
              <Link key={idx} to={path} className="nav-link">
                {Item}
              </Link>
            );
          })}
          {!role && (
            <Link to="/login" className="nav-link">Sign in</Link>
          )}
        </div>
   

        {/* Icons */}
        <div className="navbar-icons">
          <button 
            className="icon-button" 
            aria-label="Notifications" 
            onClick={() => {
              // Navigate to approval page for registrars/admins, instructors, and advisors, declaration page for students
              if (role === 'registrar') {
                navigate('/advisor-approval');
              } else if (role === 'professor') {
                navigate('/advisor-approval');
              } else if (role === 'academic_advisor') {
                navigate('/advisor-approval');
              } else {
                navigate('/major-minor-declaration');
              }
            }} 
            title={
              role === 'registrar' 
                ? 'Review Declarations & Overrides' 
                : role === 'professor' 
                ? 'Review Override Requests' 
                : role === 'academic_advisor'
                ? 'Review Declarations & Overrides'
                : 'Alerts & Overrides'
            }
          >
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
              <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
            </svg>
          </button>
          <button className="icon-button" aria-label="User Profile" onClick={() => navigate('/profile')} title='Profile'>
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
              <circle cx="12" cy="7" r="4"></circle>
            </svg>
          </button>
        </div>
      </div>
    </nav>
  );
};

export default NavigationBar;

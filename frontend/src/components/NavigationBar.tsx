import { useState} from 'react';
import './NavigationBar.css';
import { Link, useNavigate } from 'react-router-dom';
import { isAuthenticated, signOut } from '../utils/auth';


function NavigationBar() {
  const navigate = useNavigate();
 

  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [role, setRole] = useState("registrar"); 

  //Role based navbar menus
   const navbar_menus : Record<string, string[]>  = {
    "student" : ["Course Search", "Registration", "Planner", "Degree Audit", "Major Minor Declaration", "Sign out"],
    "professor" :["Course Search", "Roaster", "Grades", "Sign out"],
    "academic_advisor" : ["Course Search", "Registration Overrides", "Roaster", "Degree Audit", "Advisor Approval", "Sign out"],
    "registrar" : ["Course Search", "Registration Management", "User Management", "Course Catalog Management", "Degree Requirements Import", "Degree Requirements", "Roaster", "Grades", "Admin", "Sign out"]

  }

  const urlConverter = (text: string) => '/' + text.toLowerCase().replace(/ /g, '-');
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
          {navbar_menus[role]?.map((Item : string, idx : number) => {
            const path = urlConverter(Item);
            if (Item === 'Sign out') {
              return isAuthenticated() ? (
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
        </div>
   

        {/* Icons */}
        <div className="navbar-icons">
          <button className="icon-button" aria-label="Notifications">
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

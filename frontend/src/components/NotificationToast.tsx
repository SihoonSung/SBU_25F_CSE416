import React, { useEffect } from 'react';

export type ToastType = 'success' | 'error' | 'warning' | 'info';

interface NotificationToastProps {
    message: string;
    type: ToastType;
    onClose: () => void;
    duration?: number;
}

const NotificationToast: React.FC<NotificationToastProps> = ({
    message,
    type,
    onClose,
    duration = 3000
}) => {
    useEffect(() => {
        if (duration > 0) {
            const timer = setTimeout(() => {
                onClose();
            }, duration);
            return () => clearTimeout(timer);
        }
    }, [duration, onClose]);

    const getStyles = () => {
        switch (type) {
            case 'success':
                return {
                    backgroundColor: 'var(--success-bg)',
                    color: 'var(--success-text)',
                    border: '1px solid var(--success-border)',
                };
            case 'error':
                return {
                    backgroundColor: 'var(--error-bg)',
                    color: 'var(--error-text)',
                    border: '1px solid var(--error-border)',
                };
            case 'warning':
                return {
                    backgroundColor: 'var(--warning-bg)',
                    color: 'var(--warning-text)',
                    border: '1px solid var(--warning-border)',
                };
            case 'info':
            default:
                return {
                    backgroundColor: 'var(--info-bg)',
                    color: 'var(--info-text)',
                    border: '1px solid var(--info-border)',
                };
        }
    };

    const getIcon = () => {
        switch (type) {
            case 'success': return '✓';
            case 'error': return '✕';
            case 'warning': return '⚠';
            case 'info': return 'ℹ';
        }
    };

    return (
        <div style={{
            position: 'fixed',
            top: '20px',
            right: '20px',
            zIndex: 9999,
            padding: '16px 20px',
            borderRadius: 'var(--radius-md)',
            boxShadow: 'var(--shadow-lg)',
            display: 'flex',
            alignItems: 'flex-start',
            gap: '12px',
            maxWidth: '400px',
            animation: 'slideIn 0.3s ease-out',
            ...getStyles()
        }}>
            <span style={{ fontSize: '1.2em', lineHeight: 1 }}>{getIcon()}</span>
            <div style={{ flex: 1, whiteSpace: 'pre-wrap' }}>{message}</div>
            <button
                onClick={onClose}
                style={{
                    background: 'transparent',
                    border: 'none',
                    color: 'currentColor',
                    padding: 0,
                    cursor: 'pointer',
                    fontSize: '1.2em',
                    lineHeight: 1,
                    opacity: 0.7
                }}
            >
                ×
            </button>
            <style>{`
        @keyframes slideIn {
          from { transform: translateX(100%); opacity: 0; }
          to { transform: translateX(0); opacity: 1; }
        }
      `}</style>
        </div>
    );
};

export default NotificationToast;

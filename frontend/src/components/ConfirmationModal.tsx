import React from 'react';

interface ConfirmationModalProps {
    isOpen: boolean;
    title: string;
    message: string;
    onConfirm: () => void;
    onCancel: () => void;
    confirmText?: string;
    cancelText?: string;
    type?: 'danger' | 'primary';
}

const ConfirmationModal: React.FC<ConfirmationModalProps> = ({
    isOpen,
    title,
    message,
    onConfirm,
    onCancel,
    confirmText = 'Confirm',
    cancelText = 'Cancel',
    type = 'primary'
}) => {
    const titleId = React.useId();
    if (!isOpen) return null;

    return (
        <div style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 10000,
            backdropFilter: 'blur(4px)'
        }}>
            <div role="dialog" aria-modal="true" aria-labelledby={titleId} style={{
                backgroundColor: 'var(--bg-elevated)',
                borderRadius: 'var(--radius-lg)',
                boxShadow: 'var(--shadow-premium)',
                padding: '24px',
                maxWidth: '500px',
                width: '90%',
                animation: 'fadeIn 0.2s ease-out'
            }}>
                <h3 id={titleId} style={{
                    marginTop: 0,
                    marginBottom: '12px',
                    fontSize: '1.25rem',
                    color: 'var(--text-primary)'
                }}>
                    {title}
                </h3>

                <div style={{
                    marginBottom: '24px',
                    color: 'var(--text-secondary)',
                    whiteSpace: 'pre-wrap',
                    lineHeight: 1.6
                }}>
                    {message}
                </div>

                <div style={{
                    display: 'flex',
                    justifyContent: 'flex-end',
                    gap: '12px'
                }}>
                    {cancelText && <button
                        onClick={onCancel}
                        style={{
                            backgroundColor: 'var(--button-secondary-bg)',
                            color: 'var(--button-secondary-text)',
                            border: '1px solid var(--button-secondary-border)'
                        }}
                    >
                        {cancelText}
                    </button>}
                    <button
                        onClick={onConfirm}
                        style={{
                            backgroundColor: type === 'danger' ? 'var(--button-danger-bg)' : 'var(--button-primary-bg)',
                            color: '#ffffff',
                            border: 'none'
                        }}
                    >
                        {confirmText}
                    </button>
                </div>
            </div>
            <style>{`
        @keyframes fadeIn {
          from { opacity: 0; transform: scale(0.95); }
          to { opacity: 1; transform: scale(1); }
        }
      `}</style>
        </div>
    );
};

export default ConfirmationModal;

import React from 'react';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'outline' | 'dark';
  size?: 'sm' | 'md' | 'lg';
  fullWidth?: boolean;
}

export const Button: React.FC<ButtonProps> = ({
  children,
  variant = 'primary',
  size = 'md',
  fullWidth = false,
  className = '',
  ...props
}) => {
  const baseStyles = 'inline-flex items-center justify-center font-heading font-bold uppercase tracking-wider border-2 border-brand-primary rounded-[4px] btn-press focus:outline-none focus:ring-2 focus:ring-brand-accent transition-all duration-100 cursor-pointer select-none';
  
  const variantStyles = {
    primary: 'bg-brand-accent text-brand-primary hover:bg-brand-accent-hover shadow-hard shadow-brand-primary active:translate-x-[2px] active:translate-y-[2px] active:shadow-[2px_2px_0px_0px_#202549]',
    secondary: 'bg-brand-secondary text-white hover:bg-brand-secondary-hover shadow-hard shadow-brand-primary active:translate-x-[2px] active:translate-y-[2px] active:shadow-[2px_2px_0px_0px_#202549]',
    outline: 'bg-white text-brand-primary hover:bg-bg-base shadow-hard shadow-brand-primary active:translate-x-[2px] active:translate-y-[2px] active:shadow-[2px_2px_0px_0px_#202549]',
    dark: 'bg-brand-primary text-white hover:bg-brand-primary-hover shadow-[4px_4px_0px_0px_rgba(230,57,99,1)] hover:shadow-[4px_4px_0px_0px_rgba(230,57,99,0.8)] active:translate-x-[2px] active:translate-y-[2px] active:shadow-[2px_2px_0px_0px_rgba(230,57,99,1)]'
  };

  const sizeStyles = {
    sm: 'text-xs px-3 py-1.5 gap-1',
    md: 'text-sm px-5 py-2.5 gap-2',
    lg: 'text-base px-7 py-3.5 gap-3'
  };

  const widthStyle = fullWidth ? 'w-full' : '';

  return (
    <button
      className={`${baseStyles} ${variantStyles[variant]} ${sizeStyles[size]} ${widthStyle} ${className}`}
      {...props}
    >
      {children}
    </button>
  );
};

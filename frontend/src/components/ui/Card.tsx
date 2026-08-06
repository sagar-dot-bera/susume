import React from 'react';

interface CardProps {
  children: React.ReactNode;
  title?: string;
  subtitle?: string;
  headerAction?: React.ReactNode;
  variant?: 'white' | 'cream' | 'accent' | 'primary';
  className?: string;
  padding?: 'none' | 'sm' | 'md' | 'lg';
  shadowSize?: 'sm' | 'md' | 'lg';
  titleSize?: 'sm' | 'md' | 'lg';
}

export const Card: React.FC<CardProps> = ({
  children,
  title,
  subtitle,
  headerAction,
  variant = 'white',
  className = '',
  padding = 'md',
  shadowSize = 'md',
  titleSize = 'md'
}) => {
  const bgStyles = {
    white: 'bg-white text-text-primary',
    cream: 'bg-surface-alt text-text-primary',
    accent: 'bg-brand-accent text-brand-primary',
    primary: 'bg-brand-primary text-white border-brand-accent'
  };

  const shadowStyles = {
    sm: 'shadow-hard-sm',
    md: 'shadow-hard',
    lg: 'shadow-hard-lg'
  };

  const paddingStyles = {
    none: '',
    sm: 'p-4',
    md: 'p-6',
    lg: 'p-8'
  };

  const titleSizes = {
    sm: 'text-base font-bold',
    md: 'text-xl font-bold uppercase tracking-wide font-heading',
    lg: 'text-2xl font-extrabold uppercase tracking-wider font-heading'
  };

  return (
    <div
      className={`border-2 border-brand-primary rounded-[8px] ${bgStyles[variant]} ${shadowStyles[shadowSize]} ${className}`}
    >
      {(title || subtitle || headerAction) && (
        <div className="px-6 pt-6 pb-2 border-b-2 border-brand-primary flex items-start justify-between bg-white rounded-t-[6px]">
          <div>
            {title && <h3 className={`${titleSizes[titleSize]} text-brand-primary`}>{title}</h3>}
            {subtitle && <p className="text-xs text-text-secondary font-medium mt-1 font-sans">{subtitle}</p>}
          </div>
          {headerAction && <div className="flex items-center">{headerAction}</div>}
        </div>
      )}
      <div className={`${paddingStyles[padding]}`}>
        {children}
      </div>
    </div>
  );
};

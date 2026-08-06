import React from 'react';
import happyMascot from '../../assets/Happy chibi mascot.svg';
import warningMascot from '../../assets/Chibi warning.svg';
import readingMascot from '../../assets/Mascot reading book.svg';
import devMascot from '../../assets/dev_mascot_chibi.svg';
import loginMascot from '../../assets/log_in_chibi_girl.svg';
import signupMascot from '../../assets/sign_up_chibi_girl.svg';

interface MascotBubbleProps {
  message: React.ReactNode;
  mascot?: 'happy' | 'warning' | 'reading' | 'dev' | 'login' | 'signup';
  position?: 'left' | 'right';
  bubbleWidth?: string;
  bubbleColor?: 'white' | 'accent' | 'cream';
}

export const MascotBubble: React.FC<MascotBubbleProps> = ({
  message,
  mascot = 'happy',
  position = 'left',
  bubbleWidth = 'max-w-md',
  bubbleColor = 'white'
}) => {
  const mascotMap = {
    happy: happyMascot,
    warning: warningMascot,
    reading: readingMascot,
    dev: devMascot,
    login: loginMascot,
    signup: signupMascot
  };

  const selectedMascot = mascotMap[mascot] || happyMascot;

  const bgStyles = {
    white: 'bg-white',
    accent: 'bg-brand-accent/25 border-brand-primary',
    cream: 'bg-surface-alt'
  };

  return (
    <div className={`flex flex-col md:flex-row items-center gap-6 ${position === 'right' ? 'md:flex-row-reverse' : ''} animate-fade-in`}>
      <div className="w-24 h-24 flex-shrink-0 flex items-center justify-center p-2 bg-white border-2 border-brand-primary rounded-[8px] shadow-hard shadow-brand-primary/60">
        <img
          src={selectedMascot}
          alt={`${mascot} mascot`}
          className="w-full h-full object-contain max-h-[80px]"
        />
      </div>

      <div className={`relative flex-1 ${bubbleWidth} border-2 border-brand-primary rounded-[8px] p-5 shadow-hard ${bgStyles[bubbleColor]}`}>
        {/* Triangle pointer */}
        <div 
          className={`
            absolute hidden md:block w-3 h-3 bg-inherit border-l-2 border-t-2 border-brand-primary rotate-[-45deg] top-1/2 -translate-y-1/2
            ${position === 'right' ? '-right-[8px] rotate-[135deg]' : '-left-[8px]'}
          `}
        />
        <div className="text-sm font-sans font-medium text-text-primary leading-relaxed text-left">
          {message}
        </div>
      </div>
    </div>
  );
};

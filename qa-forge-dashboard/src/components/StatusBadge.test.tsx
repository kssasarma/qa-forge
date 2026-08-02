import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import StatusBadge from './StatusBadge';

describe('StatusBadge', () => {
  it('renders a label alongside the icon, never color alone', () => {
    render(<StatusBadge status="PASSED" />);
    expect(screen.getByText('Passed')).toBeInTheDocument();
  });

  it('renders distinct labels for failed and blocked states', () => {
    const { rerender } = render(<StatusBadge status="FAILED" />);
    expect(screen.getByText('Failed')).toBeInTheDocument();

    rerender(<StatusBadge status="BLOCKED" />);
    expect(screen.getByText('Blocked')).toBeInTheDocument();
  });
});

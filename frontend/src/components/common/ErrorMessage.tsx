/**
 * ErrorMessage.tsx
 * Responsibility: shared error line with an alert role.
 * Props: message (falls back to a generic line). API calls: none.
 */
interface Props {
  message?: string
}

export default function ErrorMessage({ message }: Props) {
  return <p className="state-message" role="alert">Error: {message ?? 'Something went wrong.'}</p>
}

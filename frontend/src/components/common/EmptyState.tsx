/**
 * EmptyState.tsx
 * Responsibility: shared empty-list line.
 * Props: message. API calls: none.
 */
interface Props {
  message: string
}

export default function EmptyState({ message }: Props) {
  return <p className="state-message">{message}</p>
}

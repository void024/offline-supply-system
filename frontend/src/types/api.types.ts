/** api.types.ts — shared cross-resource shapes. */

export interface ApiError {
  message: string
  statusCode: number
  errors?: Record<string, string[]>
}

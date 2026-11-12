/** issuer.types.ts — shapes for GET /api/v1/issuer (endpoint 10) and POST /api/v1/issuer/designate (endpoint 9). */

export interface IssuerStatus {
  device_id: string | null
  designated_at: string | null
}

export interface DesignateDto {
  device_id: string
}

export interface DesignateResponse {
  device_id: string
  designated_at: string
}

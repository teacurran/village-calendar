/**
 * Template Service
 * Provides GraphQL API calls for template CRUD operations
 */

import type { CalendarTemplate, TemplateInput } from '../types/template'

const GRAPHQL_ENDPOINT = '/graphql'

interface GraphQLResponse<T> {
  data?: T
  errors?: Array<{ message: string }>
}

interface ServiceResponse<T> {
  data: T | null
  error: string | null
}

/**
 * Get JWT token from localStorage
 */
function getAuthToken(): string | null {
  return localStorage.getItem('authToken')
}

/**
 * Make GraphQL request with authorization
 */
async function graphqlFetch<T>(
  query: string,
  variables?: Record<string, any>
): Promise<ServiceResponse<T>> {
  try {
    const token = getAuthToken()
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    }

    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }

    const response = await fetch(GRAPHQL_ENDPOINT, {
      method: 'POST',
      headers,
      body: JSON.stringify({ query, variables }),
    })

    if (!response.ok) {
      if (response.status === 401) {
        return { data: null, error: 'Unauthorized. Please log in.' }
      }
      if (response.status === 403) {
        return { data: null, error: 'Forbidden. Admin access required.' }
      }
      return { data: null, error: `HTTP error: ${response.status}` }
    }

    const result: GraphQLResponse<T> = await response.json()

    if (result.errors && result.errors.length > 0) {
      return { data: null, error: result.errors[0].message }
    }

    if (!result.data) {
      return { data: null, error: 'No data returned from server' }
    }

    return { data: result.data, error: null }
  } catch (err) {
    return { data: null, error: err instanceof Error ? err.message : 'Unknown error' }
  }
}

/**
 * Fetch all templates (includes inactive for admins)
 */
export async function fetchTemplates(
  isActive?: boolean
): Promise<ServiceResponse<CalendarTemplate[]>> {
  const query = `
    query Templates($isActive: Boolean) {
      templates(isActive: $isActive) {
        id
        name
        description
        configuration
        thumbnailUrl
        previewSvg
        isActive
        isFeatured
        displayOrder
        created
        updated
      }
    }
  `

  const result = await graphqlFetch<{ templates: CalendarTemplate[] }>(query, { isActive })

  if (result.error) {
    return { data: null, error: result.error }
  }

  return { data: result.data?.templates || [], error: null }
}

/**
 * Fetch single template by ID
 */
export async function fetchTemplate(id: string): Promise<ServiceResponse<CalendarTemplate>> {
  const query = `
    query Template($id: ID!) {
      template(id: $id) {
        id
        name
        description
        configuration
        thumbnailUrl
        previewSvg
        isActive
        isFeatured
        displayOrder
        created
        updated
      }
    }
  `

  const result = await graphqlFetch<{ template: CalendarTemplate }>(query, { id })

  if (result.error) {
    return { data: null, error: result.error }
  }

  if (!result.data?.template) {
    return { data: null, error: 'Template not found' }
  }

  return { data: result.data.template, error: null }
}

/**
 * Create new template (admin only)
 */
export async function createTemplate(
  input: TemplateInput
): Promise<ServiceResponse<CalendarTemplate>> {
  const query = `
    mutation CreateTemplate($input: TemplateInput!) {
      createTemplate(input: $input) {
        id
        name
        description
        configuration
        thumbnailUrl
        previewSvg
        isActive
        isFeatured
        displayOrder
        created
        updated
      }
    }
  `

  const result = await graphqlFetch<{ createTemplate: CalendarTemplate }>(query, { input })

  if (result.error) {
    return { data: null, error: result.error }
  }

  if (!result.data?.createTemplate) {
    return { data: null, error: 'Failed to create template' }
  }

  return { data: result.data.createTemplate, error: null }
}

/**
 * Update existing template (admin only)
 */
export async function updateTemplate(
  id: string,
  input: TemplateInput
): Promise<ServiceResponse<CalendarTemplate>> {
  const query = `
    mutation UpdateTemplate($id: ID!, $input: TemplateInput!) {
      updateTemplate(id: $id, input: $input) {
        id
        name
        description
        configuration
        thumbnailUrl
        previewSvg
        isActive
        isFeatured
        displayOrder
        created
        updated
      }
    }
  `

  const result = await graphqlFetch<{ updateTemplate: CalendarTemplate }>(query, { id, input })

  if (result.error) {
    return { data: null, error: result.error }
  }

  if (!result.data?.updateTemplate) {
    return { data: null, error: 'Failed to update template' }
  }

  return { data: result.data.updateTemplate, error: null }
}

/**
 * Delete template (admin only)
 */
export async function deleteTemplate(id: string): Promise<ServiceResponse<boolean>> {
  const query = `
    mutation DeleteTemplate($id: ID!) {
      deleteTemplate(id: $id)
    }
  `

  const result = await graphqlFetch<{ deleteTemplate: boolean }>(query, { id })

  if (result.error) {
    return { data: null, error: result.error }
  }

  return { data: result.data?.deleteTemplate || false, error: null }
}

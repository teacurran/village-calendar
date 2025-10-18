/**
 * GraphQL Service
 * Provides utilities for making GraphQL queries and mutations
 */

/**
 * Execute a GraphQL query or mutation
 */
export async function graphql<T = any>(
  query: string,
  variables?: Record<string, any>,
): Promise<T> {
  const response = await fetch("/graphql", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      query,
      variables,
    }),
  });

  if (!response.ok) {
    throw new Error(`GraphQL request failed: ${response.statusText}`);
  }

  const result = await response.json();

  if (result.errors) {
    const error = result.errors[0];
    throw new Error(error.message || "GraphQL error");
  }

  return result.data;
}

// Template Queries and Mutations

export async function fetchTemplates(
  isActive?: boolean,
  isFeatured?: boolean,
) {
  const query = `
    query Templates($isActive: Boolean, $isFeatured: Boolean) {
      templates(isActive: $isActive, isFeatured: $isFeatured) {
        id
        name
        description
        isActive
        isFeatured
        displayOrder
        configuration
        previewSvg
        createdAt
        updatedAt
      }
    }
  `;

  const data = await graphql<{ templates: any[] }>(query, {
    isActive,
    isFeatured,
  });

  return data.templates;
}

export async function fetchTemplate(id: string) {
  const query = `
    query Template($id: String!) {
      template(id: $id) {
        id
        name
        description
        isActive
        isFeatured
        displayOrder
        configuration
        previewSvg
        createdAt
        updatedAt
      }
    }
  `;

  const data = await graphql<{ template: any }>(query, { id });
  return data.template;
}

export async function createTemplate(input: {
  name: string;
  description?: string;
  configuration: any;
  isActive?: boolean;
  isFeatured?: boolean;
  displayOrder?: number;
}) {
  const mutation = `
    mutation CreateTemplate($input: TemplateInput!) {
      createTemplate(input: $input) {
        id
        name
        description
        isActive
        isFeatured
        displayOrder
        configuration
        previewSvg
        createdAt
        updatedAt
      }
    }
  `;

  const data = await graphql<{ createTemplate: any }>(mutation, { input });
  return data.createTemplate;
}

export async function updateTemplate(
  id: string,
  input: {
    name: string;
    description?: string;
    configuration: any;
    isActive?: boolean;
    isFeatured?: boolean;
    displayOrder?: number;
  },
) {
  const mutation = `
    mutation UpdateTemplate($id: String!, $input: TemplateInput!) {
      updateTemplate(id: $id, input: $input) {
        id
        name
        description
        isActive
        isFeatured
        displayOrder
        configuration
        previewSvg
        createdAt
        updatedAt
      }
    }
  `;

  const data = await graphql<{ updateTemplate: any }>(mutation, { id, input });
  return data.updateTemplate;
}

export async function deleteTemplate(id: string) {
  const mutation = `
    mutation DeleteTemplate($id: String!) {
      deleteTemplate(id: $id)
    }
  `;

  const data = await graphql<{ deleteTemplate: boolean }>(mutation, { id });
  return data.deleteTemplate;
}

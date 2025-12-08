/**
 * GraphQL queries for product catalog
 */

/**
 * Get all available products with pricing
 */
export const GET_PRODUCTS_QUERY = `
  query GetProducts {
    products {
      code
      name
      description
      price
      features
      icon
      badge
      displayOrder
    }
  }
`;

/**
 * Get a single product by code
 */
export const GET_PRODUCT_QUERY = `
  query GetProduct($code: String!) {
    product(code: $code) {
      code
      name
      description
      price
      features
      icon
      badge
      displayOrder
    }
  }
`;

/**
 * Get the default product code
 */
export const GET_DEFAULT_PRODUCT_CODE_QUERY = `
  query GetDefaultProductCode {
    defaultProductCode
  }
`;

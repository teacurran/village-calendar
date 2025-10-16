import { expect } from '@playwright/test';
import testData from '../fixtures/test-data.json';

/**
 * Mailpit API helpers for email verification
 */
export class MailpitHelper {
  private baseUrl: string;

  constructor(baseUrl: string = testData.mailpitApi.baseUrl) {
    this.baseUrl = baseUrl;
  }

  /**
   * Get all messages from Mailpit
   */
  async getMessages(): Promise<any[]> {
    const response = await fetch(`${this.baseUrl}${testData.mailpitApi.messagesEndpoint}`);

    if (!response.ok) {
      throw new Error(`Failed to fetch messages: ${response.statusText}`);
    }

    const data = await response.json();
    return data.messages || [];
  }

  /**
   * Find message by recipient email
   */
  async findMessageByRecipient(email: string, timeout: number = 10000): Promise<any> {
    const startTime = Date.now();

    while (Date.now() - startTime < timeout) {
      const messages = await this.getMessages();

      const message = messages.find(
        (m) => m.To && m.To.some((recipient: any) => recipient.Address === email)
      );

      if (message) {
        console.log(`Found email for ${email}:`, message.Subject);
        return message;
      }

      // Wait before retry
      await new Promise((resolve) => setTimeout(resolve, 1000));
    }

    throw new Error(`Email not found for ${email} within ${timeout}ms`);
  }

  /**
   * Find message by subject line
   */
  async findMessageBySubject(subject: string, timeout: number = 10000): Promise<any> {
    const startTime = Date.now();

    while (Date.now() - startTime < timeout) {
      const messages = await this.getMessages();

      const message = messages.find((m) => m.Subject && m.Subject.includes(subject));

      if (message) {
        console.log(`Found email with subject: ${subject}`);
        return message;
      }

      // Wait before retry
      await new Promise((resolve) => setTimeout(resolve, 1000));
    }

    throw new Error(`Email with subject "${subject}" not found within ${timeout}ms`);
  }

  /**
   * Get message content by ID
   */
  async getMessageContent(messageId: string): Promise<any> {
    const response = await fetch(`${this.baseUrl}/message/${messageId}`);

    if (!response.ok) {
      throw new Error(`Failed to fetch message content: ${response.statusText}`);
    }

    return await response.json();
  }

  /**
   * Verify email was sent with specific content
   */
  async verifyEmailContent(email: string, expectedContent: string[]): Promise<void> {
    const message = await this.findMessageByRecipient(email);
    const content = await this.getMessageContent(message.ID);

    // Check HTML or text content
    const body = content.HTML || content.Text || '';

    for (const text of expectedContent) {
      expect(body).toContain(text);
    }
  }

  /**
   * Clear all messages (useful for test cleanup)
   */
  async clearMessages(): Promise<void> {
    const response = await fetch(`${this.baseUrl}/messages`, {
      method: 'DELETE',
    });

    if (!response.ok) {
      throw new Error(`Failed to clear messages: ${response.statusText}`);
    }

    console.log('Mailpit messages cleared');
  }
}

/**
 * GraphQL API helpers
 */
export class GraphQLHelper {
  private endpoint: string;

  constructor(endpoint: string = 'http://localhost:8030/graphql') {
    this.endpoint = endpoint;
  }

  /**
   * Execute a GraphQL query
   */
  async query(query: string, variables?: any, authToken?: string): Promise<any> {
    const headers: HeadersInit = {
      'Content-Type': 'application/json',
    };

    if (authToken) {
      headers['Authorization'] = `Bearer ${authToken}`;
    }

    const response = await fetch(this.endpoint, {
      method: 'POST',
      headers,
      body: JSON.stringify({ query, variables }),
    });

    if (!response.ok) {
      throw new Error(`GraphQL request failed: ${response.statusText}`);
    }

    const result = await response.json();

    if (result.errors && result.errors.length > 0) {
      throw new Error(`GraphQL errors: ${JSON.stringify(result.errors)}`);
    }

    return result.data;
  }

  /**
   * Create a test template
   */
  async createTemplate(name: string, description: string, basePrice: number, authToken: string): Promise<any> {
    const mutation = `
      mutation CreateTemplate($input: TemplateInput!) {
        createTemplate(input: $input) {
          id
          name
          description
          basePrice
        }
      }
    `;

    const variables = {
      input: {
        name,
        description,
        basePrice,
        paperType: 'MATTE',
        active: true,
      },
    };

    return await this.query(mutation, variables, authToken);
  }

  /**
   * Delete a template by ID
   */
  async deleteTemplate(templateId: string, authToken: string): Promise<void> {
    const mutation = `
      mutation DeleteTemplate($id: ID!) {
        deleteTemplate(id: $id)
      }
    `;

    await this.query(mutation, { id: templateId }, authToken);
  }

  /**
   * Get all templates
   */
  async getTemplates(): Promise<any[]> {
    const query = `
      query GetTemplates {
        templates {
          id
          name
          description
          basePrice
          paperType
          active
        }
      }
    `;

    const result = await this.query(query);
    return result.templates;
  }

  /**
   * Update order status
   */
  async updateOrderStatus(orderId: string, status: string, authToken: string): Promise<any> {
    const mutation = `
      mutation UpdateOrderStatus($input: OrderUpdateInput!) {
        updateOrder(input: $input) {
          id
          status
          updatedAt
        }
      }
    `;

    const variables = {
      input: {
        id: orderId,
        status,
      },
    };

    return await this.query(mutation, variables, authToken);
  }
}

/**
 * Test data seeding helper
 */
export class TestDataSeeder {
  private graphql: GraphQLHelper;

  constructor(graphqlEndpoint?: string) {
    this.graphql = new GraphQLHelper(graphqlEndpoint);
  }

  /**
   * Seed test templates
   */
  async seedTemplates(authToken: string): Promise<void> {
    console.log('Seeding test templates...');

    for (const template of testData.templates) {
      try {
        await this.graphql.createTemplate(
          template.name,
          template.description,
          template.basePrice,
          authToken
        );
        console.log(`Created template: ${template.name}`);
      } catch (error) {
        console.warn(`Template "${template.name}" may already exist:`, error);
      }
    }
  }

  /**
   * Clean up test data
   */
  async cleanup(authToken: string): Promise<void> {
    console.log('Cleaning up test data...');

    const templates = await this.graphql.getTemplates();

    for (const template of templates) {
      if (template.name.includes('Test') || template.name.includes('2026')) {
        try {
          await this.graphql.deleteTemplate(template.id, authToken);
          console.log(`Deleted template: ${template.name}`);
        } catch (error) {
          console.warn(`Failed to delete template ${template.id}:`, error);
        }
      }
    }
  }
}

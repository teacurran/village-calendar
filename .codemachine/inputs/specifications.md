# Project Specifications

# Village Calendar

## References
The calendar generator and UI were prototyped in /Users/tea/dev/VillageCompute/code/VillageCMS/admin  that project should remain read-only reference.

## Technology Stack

### Backend (Quarkus Framework)
- **Framework**: Quarkus 3.26.2
- **Java Version**: Java 21
- **ORM**: Hibernate ORM with Panache (active record pattern)
- **Database**: PostgreSQL 17+ with PostGIS extensions
- **API Styles**:
    - GraphQL (SmallRye GraphQL)
- **Job Processing**:
    - Quarkus Scheduler for periodic tasks
    - Custom DelayedJob system with priority queues and retry logic
    - Vert.x EventBus for async operations
- **Health Checks**: SmallRye Health for AWS ALB integration

### Frontend (Vue.js)
- **Framework**: Vue 3.5+ (Composition API)
- **UI Library**: PrimeVue 4.2+ (Aura theme)
- **Icon Library**: PrimeIcons 7.0+
- **CSS Framework**: TailwindCSS 4.0+
- **State Management**: Pinia
- **Routing**: Vue Router 4.5+
- **Internationalization**: Vue I18n
- **Build Tool**: Vite 6.1+
- **TypeScript**: ~5.7.3
- **Integration**: Quinoa plugin (seamless Quarkus-Vue integration)

### Infrastructure & Deployment
- **Infrastructure as Code**: Terraform 1.7.4
    - AWS Provider 5.3
    - Cloudflare Provider 4.0
- **Configuration Management**: Ansible
- **Container Orchestration**: Kubernetes
- **Database Versioning**: MyBatis Migrations (mirations maven module)
- **CDN**: Cloudflare
- **DNS**: Cloudflare DNS
- **Email Service**: AWS SES was considered, currently using GoogleWorkspace SMTP because volume is low
- **Tunnel**: Cloudflare Tunnel for secure ingress
- **VPN**: WireGuard
- **Object Storage**: Cloudflare R2
- **State Management**: Terraform state in S3 with DynamoDB locking

### Analytics & Monitoring
- **PageView**: Page view tracking
- **AnalyticsRollup**: Analytics aggregation
- **Server**: Server instance tracking
- **ServerStatus**: Server health monitoring

### Public-Facing Features

1. **Calendar Generation**
    - Custom calendar templates
    - Hebrew calendar integration (SunCalc library)
    - Moon phase calculations (Proj4J)
    - Astronomical event tracking
    - PDF export with SVG rendering (Batik)

2. **Job Processing**
    - Generic DelayedJob system with locking mechanism
    - Configurable priority queues
    - Event-driven execution via Vert.x EventBus
    - Scheduled fallback processing
    - Exponential backoff retry strategy
    - Async job monitoring

3. **Observability**
    - Distributed tracing with OpenTelemetry
    - Jaeger trace visualization
    - Prometheus metrics
    - Health check endpoints for load balancers
    - SQL logging (configurable)

4. **DelayedJobs**
    - Email sending and other async tasks are handled with the DelayedJob interface. This is loosely designed after DelayedJob in Ruby On Rails.  Other Async jobs should follow the same pattern.

## API Endpoints

### REST Endpoints (CalendarResource.java)
- `POST /api/calendar/generate` - Generate calendar SVG (returns XML/SVG)
- `POST /api/calendar/generate-json` - Generate calendar SVG (returns JSON with svg field)
- `POST /api/calendar/generate-pdf` - Generate calendar PDF for download
- `GET /api/calendar/holidays?year={year}&country={country}` - Get holidays for year (currently returns empty set)
- `GET /api/calendar/themes` - Get available theme configurations

### REST Endpoints (SessionCalendarResource.java)
Session-based calendar management for anonymous users:
- `GET /api/session-calendar/current` - Get current session's calendar (requires `X-Session-ID` header)
- `POST /api/session-calendar/save` - Save/create calendar for session (requires `X-Session-ID` header)
- `POST /api/session-calendar/from-template/{templateId}` - Create calendar from template for session
- `PUT /api/session-calendar/{id}/autosave` - Auto-save calendar changes

### REST Endpoints (CartResource.java)
Shopping cart management (stub implementation):
- `GET /api/cart/items` - Get cart items (returns empty cart)
- `POST /api/cart/items` - Add item to cart (stub - returns success message)
- `DELETE /api/cart/items/{itemId}` - Remove item from cart (stub)
- `DELETE /api/cart/clear` - Clear cart (stub)

### REST Endpoints (UserCalendarResource.java)
User calendar management (requires authentication):
- `GET /api/calendar-templates/user/calendars` - Get all calendars for authenticated user
- `POST /api/calendar-templates/user/save` - Save or update user calendar
- `GET /api/calendar-templates/user/calendars/{id}/preview` - Get calendar preview SVG
- `DELETE /api/calendar-templates/user/calendars/{id}` - Delete user calendar

## Calendar Workflow: Templates vs User Calendars

**Templates (Admin-Only, GraphQL)**
- Reusable calendar configurations that serve as starting points
- Created/managed by admins via GraphQL mutations
- Visible to all users as starting templates
- Accessed via "Browse Templates" or "Templates" button (admin only)

**User Calendars (Authenticated Users, REST)**
- Personal saved calendars owned by specific users
- Created when user clicks "Save Calendar" button
- Private to the user who created them
- Listed in "My Saved Calendars" dialog

**Session Calendars (Anonymous Users, REST)**
- Temporary calendars for unauthenticated users
- Automatically created and updated via session ID
- Converted to user calendars upon authentication

### Complete User Workflows

**Workflow 1: Create Calendar from Blank**
1. User clicks "Create New Calendar" button
2. CalendarGenerator loads with default configuration
3. User edits settings (theme, layout, events, etc.)
4. Changes auto-save to session storage (anonymous) or user calendar (authenticated)
5. User clicks "Save Calendar" → Creates UserCalendar record
6. Admin can click bookmark icon "Save as Template" → Creates CalendarTemplate via GraphQL

**Workflow 2: Create Calendar from Template**
1. User browses templates on home page or clicks "Templates" button (admin)
2. User selects a template
3. GraphQL `templates` query loads template configuration
4. CalendarGenerator applies template config
5. User edits as desired (auto-saved to session/user)
6. User clicks "Save Calendar" → Creates UserCalendar based on template

**Workflow 3: Load Saved Calendar**
1. User clicks "My Saved Calendars"
2. REST GET `/api/calendar-templates/user/calendars` lists user's calendars
3. User selects a calendar
4. Configuration loads into CalendarGenerator
5. User can edit and re-save

**Workflow 4: Admin Creates Template (Bookmark Icon)**
1. Admin creates/edits calendar in CalendarGenerator
2. Admin clicks bookmark icon "Save as Template"
3. GraphQL `createTemplate` mutation saves template
4. Template becomes available to all users

### API Usage by Feature

| Feature | API Type | Endpoint/Query |
|---------|----------|----------------|
| Browse templates (home page) | GraphQL | `templates(isActive: true)` |
| Load template | GraphQL | `template(id)` |
| **Save as Template** (bookmark) | GraphQL | `createTemplate(input)` |
| Update template | GraphQL | `updateTemplate(id, input)` |
| List my calendars | REST | `GET /api/calendar-templates/user/calendars` |
| **Save calendar** (regular save) | REST | `POST /api/calendar-templates/user/save` |
| Session auto-save | REST | `POST /api/session-calendar/save` |

### GraphQL Endpoints

**Template Management (TemplateGraphQL.java):**
- **Query** `templates(isActive: Boolean, isFeatured: Boolean)` - Browse available templates
- **Query** `template(id: String!)` - Get single template by ID
- **Mutation** `createTemplate(input: TemplateInput!)` - Create new template (admin only)
- **Mutation** `updateTemplate(id: String!, input: TemplateInput!)` - Update template (admin only)
- **Mutation** `deleteTemplate(id: String!)` - Soft-delete template (admin only)

**Session Management (SessionResolver.java):**
- **Query** `sessionCalendars(sessionId: String!)` - Get all calendars for guest session
- **Query** `hasSessionCalendars(sessionId: String!)` - Check if session has calendars
- **Mutation** `convertGuestSession(sessionId: String!)` - Convert guest session to user account

**Calendar Management (CalendarGraphQL.java):**
- **Query** `me` - Get current authenticated user
- **Query** `myCalendars` - Get all calendars for current user
- **Query** `calendar(id: String!)` - Get single calendar by ID
- **Mutation** `createCalendar(input: CalendarInput!)` - Create new calendar
- **Mutation** `updateCalendar(id: String!, input: CalendarUpdateInput!)` - Update calendar
- **Mutation** `deleteCalendar(id: String!)` - Delete calendar
- **Mutation** `duplicateCalendar(id: String!)` - Duplicate calendar

**Cart Management (CartGraphQL.java):**
- **Query** `cart` - Get current user's shopping cart (stub - returns empty cart)
- **Mutation** `addToCart(input: AddToCartInput!)` - Add calendar to cart (stub)
- **Mutation** `updateCartItemQuantity(itemId: ID!, quantity: Int!)` - Update item quantity (stub)
- **Mutation** `removeFromCart(itemId: ID!)` - Remove item from cart (stub)
- **Mutation** `clearCart` - Clear all items from cart (stub)

### Frontend-Backend Integration Notes

**Current Status:**
- ✅ CalendarGenerator.vue imported from VillageCMS (3,460 lines)
- ✅ Backend REST endpoints exist for calendar generation (`/api/calendar/*`)
- ✅ Backend REST endpoints created for session calendar management (`/api/session-calendar/*`)
- ✅ Backend GraphQL endpoints exist for templates, sessions, calendars
- ✅ CalendarBrowser.vue updated with "Create New Calendar" button
- ⚠️ CalendarGenerator.vue uses REST endpoints that need adaptation:
  - `/api/calendar-templates/*` endpoints → Need to migrate to GraphQL queries/mutations
  - `/api/cart/*` endpoints → To be implemented for e-commerce integration
  - `/api/calendar/holidays` endpoint → Currently returns empty set, needs holiday calculation service

**Recent Changes (2025-10-17):**

**Phase 1: Initial Import**
1. **Imported CalendarGenerator.vue** from VillageCMS with full functionality (3,460 lines)
2. **Created SessionCalendarResource.java** to support anonymous user calendars with session-based storage
3. **Added `/api/calendar/holidays` endpoint** (placeholder returning empty set)
4. **Updated CalendarBrowser.vue** with prominent "Create New Calendar" and "Browse Templates" buttons in hero section

**Phase 2: API Integration (Completed)**
1. ✅ **Created sessionService.ts** - Frontend session ID management with localStorage persistence
2. ✅ **Updated CalendarGenerator.vue** - All session-calendar API calls now use `sessionFetch` with `X-Session-ID` header
3. ✅ **Created graphqlService.ts** - GraphQL client utilities with template CRUD operations
4. ✅ **Migrated template APIs to GraphQL** - All template operations now use GraphQL queries/mutations
5. ✅ **Implemented HolidayService.java** - US Federal holidays calculation service
6. ✅ **Created CartResource.java** - Stub implementation for shopping cart endpoints

**Completed Integration:**
- ✅ Session management: CalendarGenerator uses `X-Session-ID` header via sessionFetch
- ✅ Template operations: Migrated from REST to GraphQL (fetchTemplates, createTemplate, updateTemplate)
- ✅ Holiday service: Returns US Federal holidays for any given year
- ✅ Cart endpoints: Stub implementation to prevent 404 errors

**Remaining Work:**
1. Full cart/e-commerce implementation (currently stubbed)
2. Add more holiday sets (Jewish, Hebrew Religious, etc.)
3. Stripe integration for payment processing
4. Order management system

## Infrastructure

### AWS Services
- **SES**: Transactional email delivery with bounce handling - Moved to GoogleWorkspace SMTP

### Cloudflare Services
- **DNS**: Primary DNS provider
- **CDN**: Global content delivery
- **Tunnel**: Secure ingress to on-premises/private resources
- **R2**: S3-compatible object storage
- **Origin Certificates**: SSL/TLS certificates
- **Redirect Rules**: URL redirection management
- **DDoS Protection**: Automatic DDoS mitigation

### Kubernetes Configuration
- **Environments**:
    - Beta (calendar-beta.villagecompute.com)
    - Production (calendar.villagecompute.com)
- **Deployment**: Automated via Ansible
- **Image Registry**: Docker
- **Health Probes**: Readiness and liveness checks
- **Resource Limits**: CPU and memory constraints

### Monitoring & Observability
- **Distributed Tracing**: Jaeger for request flow visualization
- **Metrics**: Prometheus for time-series metrics
- **Logs**: Centralized logging (configuration in observability/)
- **Health Checks**: SmallRye Health endpoints
- **Debug Logging**: Configurable SQL and application logging

### Development Infrastructure
- **Local**: Docker Compose with PostgreSQL, Jaeger, Mailpit
- **VPN**: WireGuard
- **CI/CD**: GitHub Actions with WireGuard integration -> Proxmox for k3s deployment
- **State Management**: Terraform state in S3 with DynamoDB locking

# Features

Village Calendar is a site that lets users create custom full-year calendars with various options including:
  - custom events
  - emojis
  - holiday sets
  - astronomical data such as moon projections

## Calendars

### Calendar Generator Interface (Imported from VillageCMS)
The primary calendar creation interface is available at `/generator` and provides:

**Template Workflow (User Experience):**
1. **Start**: Users begin with either a blank calendar or select from available templates
2. **Edit**: Make changes to calendar settings, add events, customize appearance
3. **Preview**: See real-time SVG preview of the full year calendar
4. **Save**:
   - Anonymous users: Calendar saved to session storage (persisted via session ID)
   - Authenticated users: Calendar saved to their account
   - Admin users: Additional "Save as Template" option (bookmark icon) to create reusable templates

**Calendar Features:**
- **Calendar Types**:
  - Gregorian (standard) calendar
  - Hebrew lunar calendar with traditional month names
- **Layout Styles**:
  - Grid (12x31): All months in a compact grid format
  - Traditional (4x3): Classic month-by-month layout
  - Weekday Aligned Grid (12x37): Grid aligned by day of week
- **Themes**:
  - Default (Black & White)
  - Vermont Weekends (green-tinted weekends)
  - Rainbow Weekends (colorful weekend highlights)
  - Rainbow Days (Warm)
  - Rainbow Days (Cool)
- **Display Options**:
  - Week numbers
  - Compact mode
  - Day numbers and names (configurable for grid layouts)
  - Grid lines
  - Weekend highlighting
  - Rotated month names (for grid layouts)
- **Moon Display**:
  - None
  - Moon Phases (small icons showing new/full/quarter moons)
  - Moon Illumination (detailed rendering with rotation based on observer location)
  - Customizable moon size, position, colors, and border
- **Custom Events**:
  - Add custom dates with emojis
  - Optional event titles
  - Emoji positioning (top-left, top-right, bottom-left, bottom-right, center)
- **Holidays**:
  - US Federal Holidays
  - Jewish Holidays
  - Hebrew Religious Holidays (for Hebrew calendar type)
  - Customizable holiday colors
- **Color Customization**:
  - Year text color
  - Month names color
  - Day numbers color
  - Day names color
  - Grid line color
  - Weekend background color
  - Holiday text color
  - Custom date color
  - Moon dark/light colors

**User Actions:**
- Real-time preview with zoom controls (zoom in, zoom out, reset)
- Download PDF (35" x 23" print size)
- Save calendar to account (requires authentication)
- Save as template (admin only, via bookmark icon)
- Add to cart for print order

### Template Management (Admin)
Admin users have access to template management via the "Templates" button:
- **Browse Templates**: View all created templates in a DataTable
- **Load Template**: Apply template configuration to current calendar
- **Update Template**: Save changes back to an existing template
- **Duplicate Template**: Create a copy of a template with a new name
- **Template Properties**:
  - Name (required)
  - Description (optional)
  - Active status (controls visibility to non-admin users)
  - Featured status (highlights template in gallery)
  - Display order (for template gallery sorting)
  - Full configuration object (all calendar settings)

### Session-Based Calendar Storage
To support anonymous users working on calendars before authentication:
- Calendars are stored with a session ID when user is not authenticated
- Session ID is generated on frontend and persisted in browser storage
- Upon login/authentication, a "Convert Guest Session" mutation transfers all session calendars to the authenticated user account
- Backend provides:
  - GraphQL query: `sessionCalendars(sessionId: String!)` - retrieve all calendars for a session
  - GraphQL query: `hasSessionCalendars(sessionId: String!)` - check if conversion is needed
  - GraphQL mutation: `convertGuestSession(sessionId: String!)` - transfer session calendars to user

### Calendar Persistence
- **Anonymous Users**: Calendar configuration auto-saved to session storage
- **Authenticated Users**: Calendars saved to database via user account
- **Template System**: Admins can save any calendar configuration as a reusable template
- Users can save multiple calendars to their account
- Users can download a PDF of their calendar
- Users can place orders for printed versions of their calendar (via Stripe)
- Users can share a read-only link to their calendar
  - Users viewing the read-only copy can click a button to "Make a Copy" which will copy the calendar to their own account

## Login
- Google/Facebook/Apple Auth
- Ability to log-in, log-out

## Bootstrap
- a special URL /bootstrap will exist to create the first admin user if there are currently no users in the database

## Admin
- admin users will have the ability to create calendars and save them as templates
- creating a calendar template should be the same interface as the front-end for users, with the addition of a "Save as Template" button
- admins of this app will need to be able to create the calendar templates, see orders, and mark them as delivered, add notes, cancel, etc...
- site analytics will be collected and visible to admins

## Security
- quarkus security
- HTTPS everywhere
- CSRF protection
- Rate limiting on API endpoints
- Content Security Policy headers
- Input validation and sanitization

## Business Model & Pricing

### Product Offerings
- **Digital Only**: Free tier with watermarked PDFs or pay-per-download for high-quality PDFs
- **Print Orders**:
  - Single calendar: $[TBD] + shipping
  - Bulk discounts: 5+ calendars get [X]% off, 10+ get [Y]% off
  - Premium options: thicker paper
- **Subscription Model**
  - Monthly calendar, updated with events you add

### Physical Product Specifications
- **Standard Product**:
  - Size: 36" x 23" (exact might be in the generation code)
  - Paper: 100lb cover stock, matte finish
- **Premium Options**:
  - Thicker paper

### Production & Fulfillment
- **Production Timeline**:
  - Standard: 3-5 business days for printing + shipping time
  - Rush orders: 1-2 business days (+$X premium - disabled initially)
- **Fulfillment Process**:
  - Automated print queue system
  - Quality control checklist before shipping
  - Batch processing for efficiency
- **Shipping Options**:
  - Domestic: USPS First Class (5-7 days), Priority (2-3 days), Express (1-2 days)
  - International: Standard (10-21 days), Express (5-10 days)
  - Shipping cost calculator based on destination ZIP/postal code
  - Package tracking for all orders
- **Shipping Providers**:
  - Integration with ShipStation or EasyPost for label generation
  - Real-time rate shopping across carriers
  - Automatic tracking number email notifications

## Order Management

### Order Lifecycle
1. **Order Placement**: User selects options, proceeds to checkout
2. **Payment Processing**: Stripe checkout session
3. **Order Confirmation**: Email with order details and timeline
4. **Production Queue**: Order enters print queue with priority
5. **Quality Check**: Admin reviews printed calendar
6. **Shipping**: Label generation and package tracking
7. **Delivery Confirmation**: Customer notification
8. **Follow-up**: Request review/feedback email (7 days after delivery)

### Admin Order Management Interface
- **Order Dashboard**:
  - Filter by status: Pending, In Production, Shipped, Delivered, Cancelled
  - Search by order number, customer email, date range
  - Bulk status updates
  - Print queue management with drag-and-drop prioritization
- **Order Details Page**:
  - Customer information (name, email, shipping address)
  - Calendar preview with zoom capability
  - Order items with pricing breakdown
  - Production notes field
  - Status history timeline
  - Shipping tracking integration
  - Refund/cancel actions with reason tracking
  - Customer communication log
  - Reprint option for errors
- **Shipping Management**:
  - Batch label printing
  - Packing slip generation
  - Mark as shipped with tracking number
  - Shipping cost reconciliation

### Order Notifications (Email System)
- **Customer Emails**:
  - Order confirmation with calendar preview
  - Payment receipt/invoice
  - Production started notification
  - Shipped notification with tracking link
  - Delivery confirmation
  - Review request (post-delivery)
- **Admin Notifications**:
  - New order alert (email + optional SMS/Slack)
  - Low inventory warnings (if maintaining stock of materials)
  - Failed payment alerts
  - Customer support inquiries

## Payment Processing

### Stripe Integration
- **Checkout Flow**:
  - Stripe Checkout Sessions for secure payment
  - Support for credit/debit cards, Apple Pay, Google Pay
  - Save payment methods for returning customers
  - Address validation during checkout
- **Tax Calculation**:
  - Stripe Tax integration for automatic sales tax calculation
  - Support for US state sales tax, VAT, GST
  - Tax-exempt organization handling
- **Invoicing**:
  - Automatic invoice generation via Stripe
  - PDF invoices sent via email
  - Invoice history in user account
- **Failed Payments**:
  - Automatic retry logic (3 attempts over 5 days)
  - Email notifications to customer with payment update link
  - Order auto-cancellation after final failed attempt
- **Refunds & Cancellations**:
  - Full refund policy if cancelled before production starts
  - Partial refund policy for production errors (reprint + partial refund option)
  - Refund processing via Stripe API
  - Refund notification emails
  - Refund reason tracking for analytics

## User Experience & Features

### Onboarding Flow
- **First Visit**:
  - Quick tour overlay highlighting key features (dismissible)
  - "Start with Template" vs "Start from Scratch" choice
  - Example calendars gallery for inspiration
- **Guest Experience**:
  - Full editor access without login
  - "Sign in to save" prompt when attempting to leave
  - Session persistence (localStorage)
  - Convert guest calendar to saved calendar on login

### Calendar Editor Enhancements
- **Preview & Proof**:
  - High-fidelity preview mode (exact print representation)
  - Print-safe area indicators
  - Bleed marks for professional printing
  - Before-order proof step with "I approve this design" checkbox
- **Collaboration Features** (future):
  - Share edit link (not just read-only)
  - Comment/feedback system on calendar designs
  - Version history with rollback capability
- **Advanced Features**:
  - Import holidays from Google Calendar, iCal
  - Bulk event import via CSV
  - AI-powered event suggestions based on patterns
  - Photo upload for background images (with licensing confirmation)
  - Custom fonts (curated list for print quality)

### User Account Management
- **Profile Settings**:
  - Basic info: name, email, phone (optional)
  - Default shipping address with multiple address support
  - Communication preferences (marketing, order updates)
  - Saved payment methods (via Stripe)
- **Account Security**:
  - Email verification required for new accounts
  - Two-factor authentication (optional, via authenticator app)
  - Login history and active sessions
  - Account deletion with data export option (GDPR compliance)
- **My Calendars**:
  - Grid/list view of all saved calendars
  - Quick actions: Edit, Duplicate, Share, Delete, Order
  - Calendar thumbnails with last modified date
  - Folder/tag organization for power users
  - Search and filter calendars by name, date, tags
- **Order History**:
  - List of all past orders with status
  - Reorder button (use same calendar design)
  - Track shipment links
  - Download invoices
  - Request support/return

### Customer Support
- **Help Center**:
  - FAQ section (searchable)
  - Video tutorials for calendar creation
  - Design tips and best practices
  - Printing guidelines (resolution, color modes, etc.)
  - Shipping & returns information
- **Contact Support**:
  - In-app contact form (authenticated users include order context)
  - Email support: support@villagecalendar.com
  - Expected response time: 24-48 hours (weekdays)
  - Ticket tracking system
- **Support Ticketing System**:
  - Integration with helpdesk software (Zendesk, Freshdesk, or custom)
  - Automated ticket creation from contact forms
  - Priority levels based on issue type (order problem = high priority)
  - Customer satisfaction ratings after resolution

## Marketing & Growth

### SEO & Discovery
- **Technical SEO**:
  - Server-side rendering for public pages (calendar gallery, templates)
  - Semantic HTML with proper schema.org markup
  - Optimized meta tags, Open Graph, Twitter Cards
  - Sitemap generation and submission
  - Fast page loads (Lighthouse score 90+)
- **Content Marketing**:
  - Public calendar gallery (opt-in for users to showcase)
  - Blog with calendar design ideas, holiday guides, organization tips
  - Template showcase pages (SEO landing pages)
  - User-generated content encouragement

### Social Features
- **Sharing**:
  - Share calendar preview on social media with attractive OG images
  - "Made with Village Calendar" watermark on free tier (removable on paid)
  - Embed code for calendar on user websites
- **Referral Program**:
  - Give $10, Get $10 referral credits
  - Unique referral links for each user
  - Referral dashboard showing earned credits
  - Auto-apply credits to next purchase
- **Community**:
  - User reviews and ratings on templates
  - Featured calendar of the month
  - Design contests with prizes

### Email Marketing
- **Transactional Emails** (already covered in Order Notifications)
- **Marketing Campaigns**:
  - Welcome series for new users (3-email onboarding)
  - Abandoned cart recovery (guest users who didn't complete order)
  - Seasonal promotions (back-to-school, holidays, new year)
  - Re-engagement campaigns for inactive users
  - Template launch announcements
- **Email Management**:
  - Integration with email service (SendGrid, Mailgun, or current GoogleWorkspace)
  - Unsubscribe management (honor preferences)
  - Email analytics (open rates, click rates, conversions)

### Analytics & Conversion Tracking
- **User Behavior Analytics**:
  - Calendar creation funnel: Start → Edit → Save → Order
  - Drop-off point identification
  - A/B testing framework for pricing, CTA buttons, layouts
  - Heatmaps and session recordings (Hotjar, FullStory)
- **Business Metrics Dashboard** (Admin):
  - Revenue: Daily, weekly, monthly, yearly
  - Conversion rates: Visitor → Creator → Buyer
  - Average order value (AOV)
  - Customer lifetime value (LTV)
  - Most popular templates and features
  - Traffic sources and attribution
  - Cohort analysis for retention
- **Event Tracking**:
  - Google Analytics 4 or Mixpanel integration
  - Custom events: calendar_created, template_selected, share_clicked, order_placed, etc.
  - E-commerce tracking for revenue attribution

## Legal & Compliance

### Terms & Policies
- **Terms of Service**:
  - User responsibilities (own copyright for uploaded content)
  - Prohibited content guidelines
  - Service availability disclaimers
  - Dispute resolution and arbitration clauses
- **Privacy Policy**:
  - Data collection disclosure (what we collect and why)
  - Third-party services (Stripe, Google Auth, analytics)
  - Cookie usage explanation
  - User data rights (access, deletion, portability)
  - Contact information for privacy inquiries
- **Return & Refund Policy**:
  - Satisfaction guarantee details
  - Conditions for returns (damaged, printing errors)
  - Process for requesting refunds
  - Timeline for refund processing
- **Shipping Policy**:
  - Processing times by product type
  - Shipping methods and estimated delivery times
  - International shipping limitations
  - Lost package protocol

### Data Protection & Compliance
- **GDPR Compliance** (EU users):
  - Lawful basis for processing (consent, contract, legitimate interest)
  - Data processing agreements with third parties
  - Right to access, rectify, delete, port data
  - Breach notification procedures
- **CCPA Compliance** (California users):
  - "Do Not Sell My Personal Information" option
  - Data disclosure and deletion requests
  - Opt-out of data sales (if applicable)
- **Cookie Consent**:
  - Cookie banner for EU users with granular consent options
  - Essential vs analytics vs marketing cookies
  - Consent management platform integration
- **Data Retention**:
  - Account data: Retained until account deletion + 30 days
  - Order history: Retained for 7 years (tax/legal requirements)
  - Analytics data: Aggregated, anonymized after 26 months
  - Inactive account cleanup: Notify users before deletion (1 year+ inactive)

### Content Licensing
- **User Content**:
  - Users retain ownership of their designs
  - License to Village Calendar to produce and display (for service provision)
  - Opt-in license for public gallery showcase
- **Templates & Assets**:
  - Clear licensing for all provided images, fonts, icons
  - Attribution requirements for third-party assets
  - Emoji licensing compliance (system emojis vs custom sets)

## Technical Considerations

### Scalability & Performance
- **Caching Strategy**:
  - CDN caching for static assets and public calendar previews
  - Calendar preview image caching (R2 storage)
- **PDF Generation**:
  - Async job queue for PDF rendering (already using DelayedJob)
  - Progress indicators for long-running generations
  - PDF caching with cache invalidation on calendar edits
  - Watermarking for free-tier downloads
- **Database Optimization**:
  - Indexes on frequently queried fields (user_id, created_at, status)
  - Pagination for calendar lists and order history
  - Archive old orders to separate table (>1 year old)

### Monitoring & Alerts
- **Business-Critical Alerts**:
  - Payment processing failures (Stripe webhook issues)
  - PDF generation failures
  - Email delivery failures (bounce rates)
  - Site downtime or performance degradation
- **Alert Channels**:
  - PagerDuty or Opsgenie for critical issues
  - Slack integration for non-critical notifications
  - Email for daily/weekly reports

### Backup & Disaster Recovery
- **Data Backups**:
  - Database: Daily automated backups, retained 30 days
  - User-uploaded content: Replicated to secondary R2 bucket
  - Testing backup restoration quarterly
- **Disaster Recovery Plan**:
  - RTO (Recovery Time Objective): 4 hours
  - RPO (Recovery Point Objective): 24 hours
  - Documented recovery procedures
  - Failover testing schedule

## Launch Checklist

### Pre-Launch (MVP)
- [ ] Core calendar editor functional with save/load
- [ ] At least 10 professional templates
- [ ] Authentication (Google/Facebook/Apple)
- [ ] PDF generation and download
- [ ] Stripe integration for single calendar orders
- [ ] Basic admin panel for order management
- [ ] Terms of Service and Privacy Policy published
- [ ] Customer support email setup
- [ ] Essential analytics tracking

### Phase 2 (Post-MVP)
- [ ] Shipping provider integration and tracking
- [ ] Advanced admin features (batch processing, analytics)
- [ ] Email marketing campaigns
- [ ] Referral program
- [ ] Bulk ordering and discounts
- [ ] Premium product options
- [ ] Help center and FAQ
- [ ] Public calendar gallery

### Phase 3 (Growth)
- [ ] Mobile app (iOS/Android)
- [ ] Subscription model
- [ ] Advanced collaboration features
- [ ] API for third-party integrations
- [ ] White-label solution for businesses
- [ ] International expansion (localization)

## Open Questions / Decisions Needed
- What's the primary target market? (B2C individuals, B2B businesses, educators, non-profits?)
- In-house printing vs print-on-demand partner?
- Pricing: What's the target margin? Competitive analysis vs Shutterfly, Vistaprint, Minted?
- Free tier strategy: Watermarked PDFs or limited feature set or limited calendars?
- Should we allow commercial use of calendars (users selling their designs)?
- International launch timeline? (Currency support, international payment methods)
- Customer acquisition strategy: Paid ads, content marketing, partnerships?
- Seasonal demand: How to handle November-January peak demand for calendar orders?
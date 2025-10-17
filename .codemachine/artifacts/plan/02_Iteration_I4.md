# Project Plan: Village Calendar - Iteration 4

---

<!-- anchor: iteration-4-plan -->
## Iteration 4: PDF Generation & Asynchronous Job System

**Iteration ID:** `I4`

**Goal:** Implement complete PDF generation pipeline with asynchronous job processing, Batik SVG rendering, watermarking for free tier, Cloudflare R2 storage integration, and job monitoring capabilities.

**Prerequisites:** `I2` (Calendar functionality with astronomical calculations)

**Duration Estimate:** 3-4 weeks

**Deliverables:**
- DelayedJob executor framework (Vert.x EventBus integration)
- PDF generation job implementation (Batik SVG to PDF rendering)
- Watermarking logic for free vs paid tier
- Cloudflare R2 client for PDF storage
- PDF download endpoints (presigned URLs)
- Job status tracking UI
- Integration tests for PDF workflows

---

<!-- anchor: task-i4-t1 -->
### Task 4.1: Implement DelayedJob Executor Framework

**Task ID:** `I4.T1`

**Description:**
Create DelayedJobExecutor framework for asynchronous job processing. Implement JobExecutor service that: polls delayed_jobs table for pending jobs (run_at <= NOW, status=PENDING, not locked), locks job rows using PostgreSQL FOR UPDATE SKIP LOCKED, publishes job events to Vert.x EventBus, handles job lifecycle (PENDING → IN_PROGRESS → COMPLETED/FAILED). Implement JobWorker that consumes EventBus messages, executes job handler based on job_type, updates job status, handles retries with exponential backoff (attempts < max_attempts), logs failures. Create scheduled task (Quarkus @Scheduled) as fallback to process stuck jobs (locked_at > 5 minutes ago, reclaim). Support priority queuing (process higher priority jobs first). Write unit tests for job executor logic.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- DelayedJob pattern requirements from Plan Section "Async Job Processing"
- DelayedJob entity from I1.T8
- Vert.x EventBus documentation

**Input Files:**
- `src/main/java/com/villagecompute/calendar/model/DelayedJob.java`
- `src/main/resources/application.properties`

**Target Files:**
- `src/main/java/com/villagecompute/calendar/jobs/DelayedJobExecutor.java`
- `src/main/java/com/villagecompute/calendar/jobs/JobWorker.java`
- `src/main/java/com/villagecompute/calendar/jobs/JobHandler.java` (interface)
- `src/main/java/com/villagecompute/calendar/repository/DelayedJobRepository.java` (update from I1.T8)
- `src/test/java/com/villagecompute/calendar/jobs/DelayedJobExecutorTest.java`

**Deliverables:**
- DelayedJobExecutor service with job polling and locking
- JobWorker consuming Vert.x EventBus for job execution
- JobHandler interface for job type implementations
- Scheduled fallback for stuck job processing
- Unit tests for executor logic

**Acceptance Criteria:**
- DelayedJobExecutor polls delayed_jobs table every 5 seconds (configurable)
- Jobs locked with FOR UPDATE SKIP LOCKED (no duplicate processing by multiple workers)
- EventBus publishes job events with job ID and type
- JobWorker executes correct handler based on job_type field
- Failed jobs retry with exponential backoff (1min, 5min, 15min delays)
- Stuck jobs (locked >5 min) reclaimed by scheduled task
- Unit tests verify job locking, priority ordering, retry logic

**Dependencies:** `I1.T8` (DelayedJob entity)

**Parallelizable:** No (foundational async system)

---

<!-- anchor: task-i4-t2 -->
### Task 4.2: Integrate Apache Batik for SVG to PDF Rendering

**Task ID:** `I4.T2`

**Description:**
Add Apache Batik dependencies to Maven POM (batik-transcoder, batik-codec). Create PdfRenderingService with methods: generateSvgFromCalendar (takes Calendar entity, returns SVG string with calendar layout, events, astronomical overlays), convertSvgToPdf (uses Batik transcoder to convert SVG to PDF byte array). Implement calendar SVG template (12-month grid layout, configurable size 36" x 23" @ 300 DPI). Render calendar events as text overlays on dates. Render moon phase icons and Hebrew calendar dates if enabled in calendar config. Handle font embedding (ensure emoji and special characters render correctly). Test rendering performance and memory usage (target: <30 seconds, <1GB memory per calendar). Write unit tests for SVG generation logic.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- PDF generation requirements from Plan Section "PDF Generation"
- Batik transcoder documentation
- Calendar and Event entities from I1.T8
- Astronomical calculations from I2.T5

**Input Files:**
- `pom.xml`
- `src/main/java/com/villagecompute/calendar/model/Calendar.java`
- `src/main/java/com/villagecompute/calendar/service/AstronomicalService.java`

**Target Files:**
- `pom.xml` (add Batik dependencies)
- `src/main/java/com/villagecompute/calendar/util/pdf/PdfRenderingService.java`
- `src/main/java/com/villagecompute/calendar/util/pdf/SvgCalendarGenerator.java`
- `src/main/resources/calendar-templates/default-calendar.svg` (SVG template)
- `src/test/java/com/villagecompute/calendar/util/pdf/PdfRenderingServiceTest.java`

**Deliverables:**
- Batik integrated into project
- PdfRenderingService with SVG generation and PDF conversion
- SVG calendar template with 12-month layout
- Event and astronomical overlay rendering
- Performance testing (render 100 sample calendars, measure time/memory)
- Unit tests for SVG generation

**Acceptance Criteria:**
- Batik converts SVG to PDF without errors
- Generated PDF is 36" x 23" at 300 DPI (print-ready quality)
- Calendar events rendered on correct dates with text and emoji
- Moon phase icons appear on calendar if astronomy enabled
- Hebrew calendar dates rendered if enabled
- Rendering completes in <30 seconds for typical calendar (50 events)
- Memory usage <1GB per rendering (no memory leaks)
- Unit tests verify SVG structure, event placement

**Dependencies:** `I2.T5` (AstronomicalService for overlay data)

**Parallelizable:** Yes (independent rendering implementation)

---

<!-- anchor: task-i4-t3 -->
### Task 4.3: Implement Watermarking Logic

**Task ID:** `I4.T3`

**Description:**
Create WatermarkService for applying watermarks to PDFs. Implement two watermark modes: (1) Free tier - prominent diagonal "SAMPLE" watermark overlaid on PDF (semi-transparent, repeated across pages), (2) Paid tier - no watermark (or small "Made with Village Calendar" footer). Integrate watermarking into PdfRenderingService (add watermark parameter, apply watermark to SVG before PDF conversion or to PDF after conversion). Ensure watermark does not obscure critical calendar content (adjust opacity, positioning). Create visual samples of watermarked vs non-watermarked PDFs for testing. Write unit tests for watermark application.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- Watermarking requirements from Plan Section "PDF Generation"
- PdfRenderingService from Task I4.T2

**Input Files:**
- `src/main/java/com/villagecompute/calendar/util/pdf/PdfRenderingService.java`

**Target Files:**
- `src/main/java/com/villagecompute/calendar/util/pdf/WatermarkService.java`
- `src/main/resources/watermark-templates/free-tier-watermark.svg` (watermark overlay)
- `src/test/java/com/villagecompute/calendar/util/pdf/WatermarkServiceTest.java`

**Deliverables:**
- WatermarkService with free/paid tier modes
- Watermark SVG overlay template
- Integration into PdfRenderingService
- Visual samples of watermarked PDFs
- Unit tests for watermark application

**Acceptance Criteria:**
- Free tier PDFs have diagonal "SAMPLE" watermark (semi-transparent, gray color)
- Paid tier PDFs have no watermark or small footer text
- Watermark does not obscure event text or important calendar elements
- WatermarkService.applyWatermark(pdf, watermark=true) applies watermark
- Unit tests verify watermark presence/absence in generated PDFs

**Dependencies:** `I4.T2` (PdfRenderingService)

**Parallelizable:** Yes (can develop concurrently with R2 integration)

---

<!-- anchor: task-i4-t4 -->
### Task 4.4: Integrate Cloudflare R2 for PDF Storage

**Task ID:** `I4.T4`

**Description:**
Add AWS SDK S3 dependency to Maven POM (Cloudflare R2 is S3-compatible). Configure R2 connection in application.properties (R2 endpoint URL, access key ID, secret access key from env variables, bucket name). Create R2StorageService with methods: uploadPdf (upload PDF byte array to R2, return object URL), generatePresignedUrl (create time-limited signed URL for PDF download, valid 1 hour), deletePdf (remove PDF from R2). Organize R2 bucket structure: /calendars/{user_id}/{calendar_id}_{timestamp}.pdf. Handle upload failures with retry logic. Test R2 integration with Cloudflare account. Write integration tests for R2 operations.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- R2 storage requirements from Plan Section "Infrastructure"
- AWS SDK S3 documentation (S3-compatible API)

**Input Files:**
- `pom.xml`
- `src/main/resources/application.properties`

**Target Files:**
- `pom.xml` (add AWS SDK S3 dependency)
- `src/main/resources/application.properties` (add R2 config: endpoint, bucket, credentials)
- `src/main/java/com/villagecompute/calendar/integration/r2/R2StorageService.java`
- `src/test/java/com/villagecompute/calendar/integration/r2/R2StorageServiceTest.java`
- `docs/guides/r2-setup.md` (Cloudflare R2 bucket creation, API token setup)

**Deliverables:**
- R2StorageService with upload/download/delete methods
- R2 configuration from environment variables
- Presigned URL generation for secure downloads
- R2 bucket structure documented
- Integration tests for R2 operations
- R2 setup guide for developers

**Acceptance Criteria:**
- R2StorageService uploads PDF successfully to Cloudflare R2 bucket
- Uploaded PDF accessible via presigned URL (valid for 1 hour)
- Presigned URL expires after 1 hour (returns 403 after expiration)
- R2 bucket organized by user_id and calendar_id
- Upload retry logic handles transient network failures (3 retries)
- Integration tests upload/download PDF to/from R2
- R2 setup guide tested with new Cloudflare account

**Dependencies:** None (independent integration)

**Parallelizable:** Yes (can develop concurrently with PDF rendering)

---

<!-- anchor: task-i4-t5 -->
### Task 4.5: Implement PDF Generation Job Handler

**Task ID:** `I4.T5`

**Description:**
Create PdfGenerationJob implementing JobHandler interface. Job workflow: (1) Retrieve calendar data from database (calendar, events, config), (2) Call PdfRenderingService to generate PDF (with watermark based on user tier), (3) Upload PDF to R2 via R2StorageService, (4) Update calendar.pdf_url with R2 object URL, (5) Update job status to COMPLETED. Handle failures: (a) Calendar not found → mark job FAILED, (b) Rendering error → retry job, (c) R2 upload failure → retry job, (d) Max retries exceeded → mark job FAILED, notify admin. Implement job payload schema (calendarId, userId, watermark boolean). Write integration tests for complete PDF generation workflow.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- JobHandler interface from Task I4.T1
- PdfRenderingService from Task I4.T2
- WatermarkService from Task I4.T3
- R2StorageService from Task I4.T4

**Input Files:**
- `src/main/java/com/villagecompute/calendar/jobs/JobHandler.java`
- `src/main/java/com/villagecompute/calendar/util/pdf/PdfRenderingService.java`
- `src/main/java/com/villagecompute/calendar/util/pdf/WatermarkService.java`
- `src/main/java/com/villagecompute/calendar/integration/r2/R2StorageService.java`

**Target Files:**
- `src/main/java/com/villagecompute/calendar/jobs/PdfGenerationJob.java`
- `src/test/java/com/villagecompute/calendar/jobs/PdfGenerationJobTest.java`

**Deliverables:**
- PdfGenerationJob implementing complete PDF generation workflow
- Error handling for all failure scenarios
- Job payload schema defined
- Integration tests for PDF generation job

**Acceptance Criteria:**
- PdfGenerationJob.execute() generates PDF, uploads to R2, updates calendar entity
- Watermark applied based on job payload watermark flag
- calendar.pdf_url updated with R2 presigned URL
- Failed jobs retry with exponential backoff (up to 3 attempts)
- Job marked FAILED if calendar not found (no retry)
- Integration test creates job, executes, verifies PDF uploaded to R2
- Integration test verifies calendar.pdf_url updated in database

**Dependencies:** `I4.T1` (JobHandler), `I4.T2` (PdfRenderingService), `I4.T3` (WatermarkService), `I4.T4` (R2StorageService)

**Parallelizable:** No (requires all PDF and storage components)

---

<!-- anchor: task-i4-t6 -->
### Task 4.6: Implement PDF GraphQL Resolver and Service

**Task ID:** `I4.T6`

**Description:**
Create PdfService for managing PDF generation requests: enqueuePdfGeneration (create DelayedJob, publish to EventBus, return job ID), getPdfJobStatus (query DelayedJob by ID, return status/progress), getPdfDownloadUrl (retrieve calendar.pdf_url, generate presigned URL if needed). Implement PdfResolver with: query `pdfJob(id)` (fetch job status for polling), mutation `generatePdf(calendarId, watermark)` (authorize user, enqueue PDF job, return job ID). Add authorization checks (user can only generate PDFs for own calendars). Handle free tier limits (e.g., max 3 PDF generations per day). Write integration tests for PDF GraphQL workflows.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- PdfGenerationJob from Task I4.T5
- DelayedJobExecutor from Task I4.T1
- GraphQL schema from I1.T6

**Input Files:**
- `src/main/java/com/villagecompute/calendar/jobs/PdfGenerationJob.java`
- `src/main/java/com/villagecompute/calendar/jobs/DelayedJobExecutor.java`
- `src/main/java/com/villagecompute/calendar/api/graphql/PdfResolver.java` (stub from I1.T10)
- `api/graphql-schema.graphql`

**Target Files:**
- `src/main/java/com/villagecompute/calendar/service/PdfService.java`
- `src/main/java/com/villagecompute/calendar/api/graphql/PdfResolver.java` (updated)
- `src/test/java/com/villagecompute/calendar/api/graphql/PdfResolverTest.java`

**Deliverables:**
- PdfService with job enqueueing and status tracking
- PdfResolver with generatePdf mutation and pdfJob query
- Authorization checks (user owns calendar)
- Free tier rate limiting (3 PDFs per day)
- Integration tests for PDF workflows

**Acceptance Criteria:**
- GraphQL mutation `generatePdf(calendarId: "123", watermark: false)` enqueues job, returns job ID
- GraphQL query `pdfJob(id: "456")` returns job status (PENDING, IN_PROGRESS, COMPLETED, FAILED)
- Unauthorized PDF generation (user doesn't own calendar) returns GraphQL error
- Free tier users limited to 3 PDF generations per 24 hours (rate limiting enforced)
- Integration test: enqueue PDF job, poll status, verify COMPLETED with download URL
- Integration test verifies PDF downloaded from R2 presigned URL

**Dependencies:** `I4.T5` (PdfGenerationJob), `I4.T1` (DelayedJobExecutor)

**Parallelizable:** No (requires job system and PDF generation)

---

<!-- anchor: task-i4-t7 -->
### Task 4.7: Build PDF Status Tracking UI (Vue)

**Task ID:** `I4.T7`

**Description:**
Create Vue components for PDF generation status tracking. PdfGenerationDialog.vue (modal dialog showing PDF generation progress, polling job status), PdfStatusIndicator.vue (displays current job status: PENDING, IN_PROGRESS, COMPLETED, FAILED with icons/colors), PdfDownloadButton.vue (download button appears when job COMPLETED, downloads PDF from presigned URL). Implement job status polling: after calling generatePdf mutation, poll pdfJob query every 2 seconds until status is COMPLETED or FAILED. Display progress indicator (spinner, progress bar if job reports progress percentage). Handle errors (job failed, timeout after 5 minutes). Integrate into CalendarEditor view (add "Download PDF" button that opens dialog).

**Agent Type Hint:** `FrontendAgent`

**Inputs:**
- PDF generation UI requirements from Plan Section "PDF Generation"
- PrimeVue Dialog and ProgressSpinner components

**Input Files:**
- `frontend/src/views/CalendarEditor.vue`
- `api/graphql-schema.graphql`

**Target Files:**
- `frontend/src/components/pdf/PdfGenerationDialog.vue`
- `frontend/src/components/pdf/PdfStatusIndicator.vue`
- `frontend/src/components/pdf/PdfDownloadButton.vue`
- `frontend/src/graphql/pdf-mutations.ts`

**Deliverables:**
- PDF generation dialog with status polling
- Status indicator with icons for each job state
- Download button with presigned URL retrieval
- Integration into CalendarEditor view
- Error handling and timeout logic

**Acceptance Criteria:**
- Clicking "Download PDF" button opens PdfGenerationDialog
- Dialog shows "Generating PDF..." with spinner while job IN_PROGRESS
- Job status polled every 2 seconds via GraphQL pdfJob query
- When job COMPLETED, download button appears with PDF presigned URL
- Clicking download button downloads PDF file to browser
- If job FAILED, error message shown ("PDF generation failed, please try again")
- Dialog timeout after 5 minutes (show error if job still pending)

**Dependencies:** `I4.T6` (PdfResolver with generatePdf and pdfJob)

**Parallelizable:** Partially (can develop UI with mock data, integrate with API later)

---

<!-- anchor: task-i4-t8 -->
### Task 4.8: Implement Job Monitoring Dashboard (Admin)

**Task ID:** `I4.T8`

**Description:**
Create admin dashboard for monitoring asynchronous jobs. JobMonitoringDashboard.vue (admin view, lists all delayed jobs with filtering), JobDetailsPanel.vue (detailed job view with payload, error logs, retry history). Use PrimeVue DataTable with pagination, filtering by job_type (PdfGenerationJob, EmailJob), status (PENDING, IN_PROGRESS, COMPLETED, FAILED). Add job metrics: queue depth (pending jobs count), average processing time, failure rate. Implement admin actions: retry failed job, delete job. Protect route with admin role check. Integrate with GraphQL API (jobs query, retryJob mutation).

**Agent Type Hint:** `FrontendAgent`

**Inputs:**
- Job monitoring requirements from Plan Section "Observability"
- DelayedJob entity structure

**Input Files:**
- `frontend/src/views/AdminPanel.vue`
- `api/graphql-schema.graphql`

**Target Files:**
- `frontend/src/views/admin/JobMonitoringDashboard.vue`
- `frontend/src/components/admin/JobDetailsPanel.vue`
- `frontend/src/components/admin/JobMetrics.vue`
- `frontend/src/graphql/admin-job-queries.ts`

**Deliverables:**
- Job monitoring dashboard with data table
- Job filtering (type, status, date range)
- Job details panel with full payload and error logs
- Job metrics display (queue depth, processing time, failure rate)
- Admin actions (retry, delete job)
- GraphQL integration (admin job queries/mutations)

**Acceptance Criteria:**
- Dashboard displays all delayed jobs in DataTable (paginated)
- Filtering by status shows only jobs with selected status
- Clicking job row opens JobDetailsPanel with payload JSON and error logs
- Job metrics display current queue depth (count of PENDING jobs)
- Admin can retry failed job (creates new job with same payload)
- Non-admin users redirected from /admin/jobs route
- GraphQL query uses admin context (jobs query without user filter)

**Dependencies:** `I4.T6` (PdfService for GraphQL integration)

**Parallelizable:** Yes (can develop concurrently with PDF UI)

---

<!-- anchor: task-i4-t9 -->
### Task 4.9: Optimize PDF Rendering Performance

**Task ID:** `I4.T9`

**Description:**
Profile and optimize PDF rendering performance to meet <30 seconds target for typical calendars. Perform load testing: generate 100 sample calendars with varying complexity (10-100 events, astronomy enabled/disabled), measure rendering time and memory usage. Identify bottlenecks: SVG generation, Batik transcoding, font loading, image processing. Implement optimizations: (1) Cache SVG templates and fonts, (2) Reuse Batik transcoder instances, (3) Parallelize rendering if multiple PDFs requested (thread pool), (4) Compress PDFs before uploading to R2. Add progress reporting to job (update job with progress percentage during rendering). Write performance test suite to verify improvements.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- PdfRenderingService from Task I4.T2
- Performance requirements from Plan Section "Performance"

**Input Files:**
- `src/main/java/com/villagecompute/calendar/util/pdf/PdfRenderingService.java`
- `src/main/java/com/villagecompute/calendar/jobs/PdfGenerationJob.java`

**Target Files:**
- `src/main/java/com/villagecompute/calendar/util/pdf/PdfRenderingService.java` (optimized)
- `src/main/java/com/villagecompute/calendar/util/pdf/PdfCache.java` (caching layer)
- `src/test/java/com/villagecompute/calendar/util/pdf/PdfPerformanceTest.java`

**Deliverables:**
- Performance profiling report (before/after optimization)
- Optimizations implemented (caching, reuse, compression)
- Progress reporting in PDF generation job
- Performance test suite

**Acceptance Criteria:**
- Typical calendar (50 events, astronomy enabled) renders in <30 seconds
- Memory usage during rendering <1GB per calendar
- SVG templates and fonts cached (subsequent renders faster)
- Batik transcoder instances reused (thread pool of 5 transcoders)
- Generated PDFs compressed (DEFLATE compression, 30-50% size reduction)
- Progress percentage reported in job (0% → 50% SVG gen → 90% PDF transcode → 100% upload)
- Performance tests verify <30s target for 95th percentile

**Dependencies:** `I4.T2` (PdfRenderingService), `I4.T5` (PdfGenerationJob)

**Parallelizable:** Yes (optimization task can be developed after initial implementation)

---

<!-- anchor: task-i4-t10 -->
### Task 4.10: Write Integration Tests for PDF Generation Pipeline

**Task ID:** `I4.T10`

**Description:**
Create end-to-end integration tests for complete PDF generation pipeline. Test scenarios: (1) Generate free tier PDF - create calendar, enqueue PDF job with watermark=true, wait for job completion, verify PDF uploaded to R2 with watermark; (2) Generate paid tier PDF - enqueue job with watermark=false, verify PDF has no watermark; (3) Handle rendering failure - inject error into rendering (invalid calendar config), verify job marked FAILED and retried; (4) Job queue processing - enqueue 10 PDF jobs, verify all processed by workers, verify queue depth decreases. Use Testcontainers for PostgreSQL, mock R2 API or use test bucket. Achieve >70% code coverage for PDF and job code.

**Agent Type Hint:** `BackendAgent`

**Inputs:**
- All PDF and job components from I4 tasks
- Quarkus testing, Testcontainers documentation

**Input Files:**
- All service and job classes from I4 tasks
- `api/graphql-schema.graphql`

**Target Files:**
- `src/test/java/com/villagecompute/calendar/integration/PdfGenerationPipelineTest.java`
- `src/test/java/com/villagecompute/calendar/integration/JobQueueProcessingTest.java`

**Deliverables:**
- Integration tests for PDF generation pipeline
- Tests use GraphQL API and job execution
- R2 integration tested (real bucket or mock)
- Tests achieve >70% coverage for PDF/job code
- All tests pass with `./mvnw verify`

**Acceptance Criteria:**
- Free tier test verifies watermark present in generated PDF (PDF content inspection)
- Paid tier test verifies no watermark in PDF
- Rendering failure test triggers job retry, verifies attempt count incremented
- Job queue test enqueues 10 jobs, verifies all complete within 5 minutes
- All integration tests pass consistently (no flaky tests)
- Tests run in <120 seconds

**Dependencies:** All I4 tasks (requires complete PDF generation system)

**Parallelizable:** No (final integration testing task)

---

<!-- anchor: iteration-4-summary -->
### Iteration 4 Summary

**Total Tasks:** 10

**Completion Criteria:**
- All tasks marked as completed
- Complete PDF generation pipeline functional (job system, rendering, storage)
- Batik integration working with calendar SVG generation
- Watermarking logic implemented for free/paid tiers
- R2 storage integration working (upload, download, presigned URLs)
- PDF status tracking UI functional
- Job monitoring dashboard for admins
- Integration tests passing with adequate coverage
- Performance targets met (<30s rendering, <1GB memory)

**Risk Mitigation:**
- If Batik rendering proves unstable, evaluate alternative PDF libraries (iText, Apache PDFBox)
- If R2 integration has issues, fall back to local file storage for MVP and migrate later
- If performance targets not met, implement PDF pre-generation for popular templates (nightly batch job)

**Next Iteration Preview:**
Iteration 5 will focus on analytics, observability, and admin features: business metrics tracking, Jaeger/Prometheus integration, admin dashboard enhancements, and user analytics.

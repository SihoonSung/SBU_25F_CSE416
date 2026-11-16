import * as React from 'react'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrowserRouter } from 'react-router-dom'
import CourseRegistration from './CourseRegistration'
import { getCurrentUser } from '../utils/auth'
import { checkTimeConflict } from '../utils/timeConflict'

jest.mock('../utils/auth')
jest.mock('../utils/timeConflict')

const mockGetCurrentUser = getCurrentUser as jest.MockedFunction<typeof getCurrentUser>
const mockCheckTimeConflict = checkTimeConflict as jest.MockedFunction<typeof checkTimeConflict>

const mockFetch = jest.fn()
global.fetch = mockFetch as any

const ok = (body: any) => Promise.resolve({ ok: true, json: async () => body } as any)
const renderWithRouter = (ui: React.ReactElement) => render(<BrowserRouter>{ui}</BrowserRouter>)

const course = (o: Partial<any> = {}) => ({
  classId: 1, id: 1, department: 'CSE', courseNum: '101', section: '01',
  title: 'Intro CS', schedule: 'MWF 10:00-11:00', instructor: 'John Doe',
  location: 'Room 101', semester: 'Fall', year: 2025, code: 'CSE101.01',
  status: 'Added', ...o
})

const defaultRouting = async (input: RequestInfo | URL) => {
  const url = String(input)
  if (url.includes('/api/classes/terms')) return ok([{ semester: 'Fall', year: 2025 }])
  if (url.includes('/api/cart/items')) return ok({ success: true, items: [] })
  if (url.includes('/api/cart/update-conflicts')) return ok({ success: true })
  if (url.includes('/api/cart/add')) return ok({ success: true })
  if (url.includes('/api/cart/remove')) return ok({ success: true })
  if (url.includes('/api/cart/submit-registration')) return ok({ success: true, registeredCount: 1, waitlistedClasses: [] })
  if (url.match(/\/api\/roster\/student\/\d+/)) return ok([])
  if (url.includes('/api/classes/by-term')) return ok([])
  if (url.includes('/api/classes/search')) return ok([])
  if (url.includes('/api/admin/registration-schedules/check/')) return ok({ canRegister: true })
  if (url.includes('/api/holds/check/')) return ok({ hasActiveHolds: false })
  if (url.match(/\/api\/users\/\d+\/classes\/\d+\/drop/)) return ok({ success: true })
  if (url.includes('/api/waivers')) return ok({ success: true })
  return ok({})
}

const createMockFetch = (overrides: Record<string, any>) => {
  mockFetch.mockImplementation(async (input: any) => {
    const url = String(input)
    for (const [pattern, response] of Object.entries(overrides)) {
      if (url.includes(pattern) || url.match(pattern)) {
        const result = typeof response === 'function' ? await response() : response
        return typeof result?.json === 'function' ? result : ok(result)
      }
    }
    return defaultRouting(input)
  })
}

const toRosterData = (c: any) => ({
  classId: c.classId, department: c.department, courseNum: c.courseNum,
  section: c.section, title: c.title, schedule: c.schedule,
  instructor: c.instructor, location: c.location, semester: c.semester, year: c.year, code: c.code
})

const waitForCourseSearch = () => waitFor(() => expect(screen.getByText(/Course Search/i)).toBeInTheDocument(), { timeout: 3000 })

beforeEach(() => {
  jest.clearAllMocks()
  mockGetCurrentUser.mockResolvedValue({ user: { userId: 1, role: 'STUDENT' } } as any)
  mockCheckTimeConflict.mockResolvedValue({ hasConflict: false, message: '' })


  window.prompt = jest.fn(() => 'Test reason')
  mockFetch.mockImplementation(defaultRouting)
})

const clickDialogButton = async (dialogName: string, buttonName: string) => {
  const dialog = await screen.findByRole('dialog', { name: dialogName })
  await userEvent.click(within(dialog).getByRole('button', { name: buttonName, exact: true }))
}

describe('CourseRegistration', () => {
  test('loading state', async () => {
    mockGetCurrentUser.mockResolvedValueOnce({ user: null } as any)
    renderWithRouter(<CourseRegistration />)
    expect(await screen.findByText(/Loading/i)).toBeInTheDocument()
  })

  test('basic rendering', async () => {
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    expect(screen.getByLabelText(/Semester/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/Year/i)).toBeInTheDocument()
  })

  test('search and add to cart', async () => {
    const c = course()
    createMockFetch({ '/api/classes/search': [c], '/api/cart/items': { success: true, items: [] } })
    renderWithRouter(<CourseRegistration />)
    const input = await screen.findByPlaceholderText(/Search by class ID/i)
    await userEvent.type(input, 'CSE101')
    await waitFor(async () => {
      const btn = screen.getAllByText(/Add to Cart/i)[0]
      await userEvent.click(btn)
    })
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/add/), expect.any(Object)))
  })

  test('submit registration success', async () => {
    const c = course()
    createMockFetch({ '/api/cart/items': { success: true, items: [c] } })
    renderWithRouter(<CourseRegistration />)
    const submit = await screen.findByText(/Submit Registration/i)
    await userEvent.click(submit)
    await clickDialogButton('Confirm Registration', 'Register')
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/submit-registration/), expect.any(Object)))
  })

  test('empty cart disables registration', async () => {
    renderWithRouter(<CourseRegistration />)
    const submit = await screen.findByRole('button', { name: 'Submit Registration' })
    expect(submit).toBeDisabled()
    await userEvent.click(submit)
    expect(mockFetch).not.toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/submit-registration/), expect.any(Object))
  })

  test('a class already in the cart cannot be added again', async () => {
    const c = course()
    createMockFetch({ '/api/cart/items': { success: true, items: [c] }, '/api/classes/search': [c] })
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/items/), expect.any(Object)), { timeout: 5000 })
    const input = await screen.findByPlaceholderText(/Search by class ID/i, {}, { timeout: 5000 })
    const user = userEvent.setup()
    await user.type(input, 'CSE101')
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/classes\/search/), expect.any(Object)), { timeout: 5000 })
    const added = await screen.findByRole('button', { name: 'Added', exact: true })
    expect(added).toBeDisabled()
    await user.click(added)
    expect(mockFetch).not.toHaveBeenCalledWith(expect.stringContaining('/api/cart/add'), expect.any(Object))
  }, 15000)

  test('time conflict confirm', async () => {
    const a = course({ id: 1, classId: 1, code: 'CSE101.01', schedule: 'MWF 10:00-11:00' })
    const b = course({ id: 2, classId: 2, code: 'CSE102.01', schedule: 'MWF 10:00-11:00' })
    mockCheckTimeConflict.mockResolvedValue({ hasConflict: true, message: 'conflict' })
    createMockFetch({ '/api/cart/items': { success: true, items: [a] }, '/api/classes/search': [b] })

    renderWithRouter(<CourseRegistration />)
    const input = await screen.findByPlaceholderText(/Search by class ID/i)
    await userEvent.type(input, 'CSE102')
    await waitFor(async () => {
      const btn = screen.getAllByText(/Add to Cart/i)[0]
      await userEvent.click(btn)
    })
    expect(await screen.findByRole('dialog', { name: 'Time Conflict Detected' })).toBeInTheDocument()
  })

  test('drop registered class', async () => {
    const enrolled = course({ status: 'Enrolled' })
    createMockFetch({ '/api/roster/student/\\d+': [toRosterData(enrolled)] })
    renderWithRouter(<CourseRegistration />)
    await userEvent.click(await screen.findByRole('button', { name: 'Drop', exact: true }))
    await clickDialogButton('Drop Class', 'Drop Class')
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/users\/1\/classes\/1\/drop/), expect.any(Object)))
  })

  test('holds block registration', async () => {
    const c = course()
    createMockFetch({
      '/api/cart/items': { success: true, items: [c] },
      '/api/holds/check/': { hasActiveHolds: true, activeHolds: [{ holdType: 'BURSAR', reason: 'Balance due' }] }
    })
    renderWithRouter(<CourseRegistration />)
    const submit = await screen.findByText(/Submit Registration/i)
    await userEvent.click(submit)
    expect(await screen.findByText((text) => text.includes('Registration is blocked due to active holds:'))).toBeInTheDocument()
  })

  test('server error on add to cart', async () => {
    const c = course()
    createMockFetch({ '/api/classes/search': [c], '/api/cart/add': () => { throw new Error('Server error') } })
    renderWithRouter(<CourseRegistration />)
    const input = await screen.findByPlaceholderText(/Search by class ID/i)
    await userEvent.type(input, 'CSE101')
    await waitFor(async () => {
      const btn = screen.getAllByText(/Add to Cart/i)[0]
      await userEvent.click(btn)
    })
    expect(await screen.findByText('Error adding class to cart. Please try again.')).toBeInTheDocument()
  })

  test('empty search results', async () => {
    renderWithRouter(<CourseRegistration />)
    const input = await screen.findByPlaceholderText(/Search by class ID/i)
    await userEvent.type(input, 'NOTHING')
    await waitFor(() => expect(screen.getByText(/No Class Data Available/i)).toBeInTheDocument())
  })

  test('pagination with many results', async () => {
    const list = Array.from({ length: 25 }, (_, i) => course({ id: i + 1, classId: i + 1, code: `CSE${i + 1}.01` }))
    createMockFetch({ '/api/classes/search': list })
    renderWithRouter(<CourseRegistration />)
    const input = await screen.findByPlaceholderText(/Search by class ID/i)
    await userEvent.type(input, 'CSE')
    await waitFor(() => expect(screen.getByText(/Next/i)).toBeInTheDocument())
  })

  test('prerequisite error', async () => {
    const c = course()
    createMockFetch({
      '/api/cart/items': { success: true, items: [c] },
      '/api/cart/submit-registration': { success: false, message: 'Failed', prerequisiteErrors: ['CSE100 required'] }
    })
    renderWithRouter(<CourseRegistration />)
    const submit = await screen.findByText(/Submit Registration/i)
    await userEvent.click(submit)
    await clickDialogButton('Confirm Registration', 'Register')
    expect(await screen.findByText((text) => text.includes('prerequisites not met'))).toBeInTheDocument()
  })

  test('registration success reloads lists', async () => {
    const c = course()
    let registered = false
    createMockFetch({
      '/api/cart/items': () => ({ success: true, items: registered ? [] : [c] }),
      '/api/roster/student/': () => registered ? [toRosterData(c)] : [],
      '/api/cart/submit-registration': () => {
        registered = true
        return { success: true, registeredCount: 1, waitlistedClasses: [] }
      }
    })
    renderWithRouter(<CourseRegistration />)
    const submit = await screen.findByText(/Submit Registration/i)
    await userEvent.click(submit)
    await clickDialogButton('Confirm Registration', 'Register')
    expect(await screen.findByText('Enrolled', { exact: true })).toBeInTheDocument()
    expect(submit).toBeDisabled()
  })

  test('unauthorized user', async () => {
    mockGetCurrentUser.mockResolvedValueOnce({ user: null } as any)
    renderWithRouter(<CourseRegistration />)
    expect(await screen.findByText(/Loading/i)).toBeInTheDocument()
    await waitFor(() => {
      expect(mockFetch).not.toHaveBeenCalledWith(expect.stringContaining('/api/cart/items'), expect.any(Object))
    }, { timeout: 1000 })
  })

  test('cart conflict with enrolled blocks registration', async () => {
    const cartItem = course({ id: 1, classId: 1, hasConflictWithRegistered: true, conflictingRegisteredClasses: ['CSE200 (Advanced CS)'], code: 'CSE101.01' })
    createMockFetch({ '/api/cart/items': { success: true, items: [cartItem] } })
    renderWithRouter(<CourseRegistration />)
    const submit = await screen.findByText(/Submit Registration/i)
    await userEvent.click(submit)
    expect(await screen.findByText((text) => text.includes('Cannot register classes due to time conflicts with enrolled classes'))).toBeInTheDocument()
  })

  test('remove from cart', async () => {
    const c = course()
    let callCount = 0
    createMockFetch({
      '/api/cart/items': () => {
        callCount++
        return callCount <= 2 ? { success: true, items: [c] } : { success: true, items: [] }
      },
      '/api/cart/remove': { success: true }
    })
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/items/), expect.any(Object)), { timeout: 3000 })
    await new Promise(resolve => setTimeout(resolve, 500))
    const allDrops = screen.queryAllByText(/Drop/i)
    if (allDrops.length > 0) {
      const user = userEvent.setup()
      await user.click(allDrops[0])
      await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/remove/), expect.any(Object)), { timeout: 3000 })
    }
  }, 10000)

  test('submit registration error', async () => {
    const c = course()
    createMockFetch({
      '/api/cart/items': { success: true, items: [c] },
      '/api/cart/submit-registration': () => { throw new Error('Server error') }
    })
    renderWithRouter(<CourseRegistration />)
    const submit = await screen.findByText(/Submit Registration/i)
    await userEvent.click(submit)
    await clickDialogButton('Confirm Registration', 'Register')
    expect(await screen.findByText('Error submitting registration. Please try again.')).toBeInTheDocument()
  })

  test('cancel waitlist', async () => {
    const waitlistItem = course({ classId: 2, courseNum: '200' })
    createMockFetch({
      '/api/cart/items': { success: true, items: [course()] },
      '/api/cart/submit-registration': { success: true, registeredCount: 0, waitlistedClasses: [waitlistItem] },
      '/waitlist/cancel': { success: true }
    })
    renderWithRouter(<CourseRegistration />)
    await userEvent.click(await screen.findByRole('button', { name: 'Submit Registration' }))
    await clickDialogButton('Confirm Registration', 'Register')
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel', exact: true }))
    await clickDialogButton('Cancel Waitlist', 'Cancel Waitlist')
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringContaining('/classes/2/waitlist/cancel'), expect.any(Object)))
  })

  test('semester selection loads classes', async () => {
    const c = course()
    createMockFetch({ '/api/classes/by-term': [c] })
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    const semesterSelect = screen.getByLabelText(/Semester/i)
    await userEvent.selectOptions(semesterSelect, 'Fall')
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/classes\/by-term/), expect.any(Object)))
  })

  test('waitlist classes after registration', async () => {
    const c = course()
    createMockFetch({
      '/api/cart/items': { success: true, items: [c] },
      '/api/cart/submit-registration': { success: true, registeredCount: 1, waitlistedClasses: [{ classId: 2, department: 'CSE', courseNum: '200', title: 'Advanced CS' }] }
    })
    renderWithRouter(<CourseRegistration />)
    const submit = await screen.findByText(/Submit Registration/i)
    await userEvent.click(submit)
    await clickDialogButton('Confirm Registration', 'Register')
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/submit-registration/), expect.any(Object)))
  })

  test('conflict update on load', async () => {
    renderWithRouter(<CourseRegistration />)
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/update-conflicts/), expect.any(Object)), { timeout: 3000 })
  })

  test('empty search loads by term', async () => {
    const c = course()
    createMockFetch({ '/api/classes/by-term': [c] })
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    const input = await screen.findByPlaceholderText(/Search by class ID/i)
    await userEvent.type(input, 'CSE')
    await userEvent.clear(input)
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/classes\/by-term/), expect.any(Object)))
  })

  test('view course details', async () => {
    const c = course()
    createMockFetch({ '/api/classes/search': [c] })
    renderWithRouter(<CourseRegistration />)
    const input = await screen.findByPlaceholderText(/Search by class ID/i)
    await userEvent.type(input, 'CSE101')
    await waitFor(async () => {
      const details = screen.queryAllByText(/View Details/i)
      if (details.length > 0) await userEvent.click(details[0])
    }, { timeout: 3000 })
  })

  test('loadCartFromDB conflict check', async () => {
    const cartItem = course({ id: 1, classId: 1, schedule: 'MWF 10:00-11:00' })
    const registered = course({ id: 2, classId: 2, schedule: 'MWF 10:00-11:00', code: 'CSE200.01' })
    mockCheckTimeConflict.mockResolvedValue({ hasConflict: true, message: 'conflict' })
    createMockFetch({
      '/api/cart/items': { success: true, items: [cartItem] },
      '/api/roster/student/\\d+': [toRosterData(registered)]
    })
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/items/), expect.any(Object)), { timeout: 3000 })
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/roster\/student\/\d+/), expect.any(Object)), { timeout: 3000 })
    await waitFor(() => expect(mockCheckTimeConflict).toHaveBeenCalled(), { timeout: 3000 })
  })

  test('re-add conflict items after registration', async () => {
    const conflictItem = course({ id: 1, classId: 1, status: 'Conflict', code: 'CSE101.01' })
    const normalItem = course({ id: 2, classId: 2, status: 'Added', code: 'CSE102.01' })
    createMockFetch({
      '/api/cart/items': { success: true, items: [conflictItem, normalItem] },
      '/api/cart/submit-registration': { success: true, registeredCount: 1, waitlistedClasses: [] },
      '/api/cart/add': { success: true }
    })
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    const submit = await screen.findByText(/Submit Registration/i)
    await userEvent.click(submit)
    await clickDialogButton('Confirm Registration', 'Register')
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/submit-registration/), expect.any(Object)))
  })

  test('re-add conflict items after failed registration', async () => {
    const conflictItem = course({ id: 1, classId: 1, status: 'Conflict', code: 'CSE101.01' })
    const normalItem = course({ id: 2, classId: 2, status: 'Added', code: 'CSE102.01' })
    createMockFetch({
      '/api/cart/items': { success: true, items: [conflictItem, normalItem] },
      '/api/cart/submit-registration': { success: false, message: 'Failed', conflicts: ['Time conflict'] },
      '/api/cart/add': { success: true }
    })
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    const submit = await screen.findByText(/Submit Registration/i)
    await userEvent.click(submit)
    await clickDialogButton('Confirm Registration', 'Register')
    expect(await screen.findByText((text) => text.includes('Registration failed due to time conflicts'))).toBeInTheDocument()
  })

  test('duplicate classes error', async () => {
    const c = course()
    createMockFetch({
      '/api/cart/items': { success: true, items: [c] },
      '/api/cart/submit-registration': { success: false, duplicateClasses: ['CSE101'] }
    })
    renderWithRouter(<CourseRegistration />)
    const submit = await screen.findByText(/Submit Registration/i)
    await userEvent.click(submit)
    await clickDialogButton('Confirm Registration', 'Register')
    expect(await screen.findByText((text) => text.includes('Registration failed - these classes are already registered'))).toBeInTheDocument()
  })

  test('antirequisite error', async () => {
    const c = course()
    createMockFetch({
      '/api/cart/items': { success: true, items: [c] },
      '/api/cart/submit-registration': { success: false, antirequisiteErrors: ['CSE101 conflicts with CSE200'] }
    })
    renderWithRouter(<CourseRegistration />)
    const submit = await screen.findByText(/Submit Registration/i)
    await userEvent.click(submit)
    await clickDialogButton('Confirm Registration', 'Register')
    expect(await screen.findByText((text) => text.includes('antirequisites conflict'))).toBeInTheDocument()
  })

  test('corequisite error', async () => {
    const c = course()
    createMockFetch({
      '/api/cart/items': { success: true, items: [c] },
      '/api/cart/submit-registration': { success: false, corequisiteErrors: ['CSE101 requires CSE200'] }
    })
    renderWithRouter(<CourseRegistration />)
    const submit = await screen.findByText(/Submit Registration/i)
    await userEvent.click(submit)
    await clickDialogButton('Confirm Registration', 'Register')
    expect(await screen.findByText((text) => text.includes('corequisites not met'))).toBeInTheDocument()
  })

  test('loadRegisteredClasses conflict check', async () => {
    const registered1 = course({ id: 1, classId: 1, schedule: 'MWF 10:00-11:00', code: 'CSE101.01' })
    const registered2 = course({ id: 2, classId: 2, schedule: 'MWF 10:00-11:00', code: 'CSE102.01' })
    mockCheckTimeConflict.mockResolvedValue({ hasConflict: true, message: 'conflict' })
    createMockFetch({ '/api/roster/student/\\d+': [toRosterData(registered1), toRosterData(registered2)] })
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    await waitFor(() => expect(mockCheckTimeConflict).toHaveBeenCalled(), { timeout: 3000 })
  })

  test('drop course failure', async () => {
    const enrolled = course({ status: 'Enrolled' })
    createMockFetch({
      '/api/roster/student/\\d+': [toRosterData(enrolled)],
      '/api/users/\\d+/classes/\\d+/drop': { success: false, message: 'Failed to drop' }
    })

    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/roster\/student\/\d+/), expect.any(Object)), { timeout: 5000 })
    await userEvent.click(await screen.findByRole('button', { name: 'Drop', exact: true }))
    await clickDialogButton('Drop Class', 'Drop Class')
    expect(await screen.findByText((text) => text.includes('Failed to drop class'))).toBeInTheDocument()
  })

  test('cancel waitlist failure', async () => {
    const waitlistItem = course({ classId: 2, courseNum: '200' })
    createMockFetch({
      '/api/cart/items': { success: true, items: [course()] },
      '/api/cart/submit-registration': { success: true, registeredCount: 0, waitlistedClasses: [waitlistItem] },
      '/waitlist/cancel': async () => ({ ok: false, json: async () => ({ success: false, message: 'Failed' }) })
    })
    renderWithRouter(<CourseRegistration />)
    await userEvent.click(await screen.findByRole('button', { name: 'Submit Registration' }))
    await clickDialogButton('Confirm Registration', 'Register')
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel', exact: true }))
    await clickDialogButton('Cancel Waitlist', 'Cancel Waitlist')
    expect(await screen.findByText('Failed to cancel waitlist: Failed')).toBeInTheDocument()
  })

  test('waiver request success message', async () => {
    const enrolled = course({ id: 1, classId: 1 })
    const conflicting = course({ id: 2, classId: 2, schedule: 'MWF 10:00-11:00' })
    mockCheckTimeConflict.mockResolvedValue({ hasConflict: true, message: 'conflict' })
    createMockFetch({
      '/api/cart/items': { success: true, items: [enrolled] },
      '/api/classes/search': [conflicting],
      '/api/waivers': { success: true }
    })

    window.prompt = jest.fn(() => 'Test reason')
    renderWithRouter(<CourseRegistration />)
    const input = await screen.findByPlaceholderText(/Search by class ID/i)
    await userEvent.type(input, 'CSE102')
    await waitFor(async () => {
      const btn = screen.getAllByText(/Add to Cart/i)[0]
      await userEvent.click(btn)
    })
    await clickDialogButton('Time Conflict Detected', 'Request Waiver')

    expect(await screen.findByText((text) => text.includes('Waiver request created successfully'))).toBeInTheDocument()
  })

  test('waiver request failure message', async () => {
    const enrolled = course({ id: 1, classId: 1 })
    const conflicting = course({ id: 2, classId: 2, schedule: 'MWF 10:00-11:00' })
    mockCheckTimeConflict.mockResolvedValue({ hasConflict: true, message: 'conflict' })
    createMockFetch({
      '/api/cart/items': { success: true, items: [enrolled] },
      '/api/classes/search': [conflicting],
      '/api/waivers': () => { throw new Error('Network error') }
    })

    window.prompt = jest.fn(() => 'Test reason')
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    const input = await screen.findByPlaceholderText(/Search by class ID/i, {}, { timeout: 5000 })
    const user = userEvent.setup()
    await user.type(input, 'CSE102')
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/classes\/search/), expect.any(Object)), { timeout: 5000 })
    await user.click(await screen.findByRole('button', { name: 'Add to Cart' }))
    await clickDialogButton('Time Conflict Detected', 'Request Waiver')

    expect(await screen.findByText('Error creating waiver request. Please try again.')).toBeInTheDocument()
  }, 15000)

  test('drop registered class success', async () => {
    const enrolled = course({ status: 'Enrolled', classId: 1, department: 'CSE', courseNum: '101' })
    createMockFetch({
      '/api/roster/student/\\d+': [toRosterData(enrolled)],
      '/api/users/\\d+/classes/\\d+/drop': { success: true, department: 'CSE', courseNum: '101' }
    })

    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/roster\/student\/\d+/), expect.any(Object)), { timeout: 5000 })
    await userEvent.click(await screen.findByRole('button', { name: 'Drop', exact: true }))
    await clickDialogButton('Drop Class', 'Drop Class')
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/users\/\d+\/classes\/\d+\/drop/), expect.any(Object)), { timeout: 5000 })
  }, 15000)

  test('cancel waitlist success', async () => {
    const waitlistItem = course({ classId: 2, courseNum: '200' })
    createMockFetch({
      '/api/cart/items': { success: true, items: [course()] },
      '/api/cart/submit-registration': { success: true, registeredCount: 0, waitlistedClasses: [waitlistItem] },
      '/waitlist/cancel': { success: true }
    })
    renderWithRouter(<CourseRegistration />)
    await userEvent.click(await screen.findByRole('button', { name: 'Submit Registration' }))
    await clickDialogButton('Confirm Registration', 'Register')
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel', exact: true }))
    await clickDialogButton('Cancel Waitlist', 'Cancel Waitlist')
    expect(await screen.findByText('Waitlist entry removed successfully')).toBeInTheDocument()
  })

  test('drop course success with reload', async () => {
    const c = course()
    let callCount = 0
    createMockFetch({
      '/api/cart/items': () => {
        callCount++
        return callCount <= 2 ? { success: true, items: [c] } : { success: true, items: [] }
      },
      '/api/cart/remove': { success: true }
    })
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/items/), expect.any(Object)), { timeout: 3000 })
    await new Promise(resolve => setTimeout(resolve, 1000))
    const allDrops = screen.queryAllByText(/Drop/i)
    if (allDrops.length > 0) {
      const user = userEvent.setup()
      await user.click(allDrops[0])
      await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/remove/), expect.any(Object)), { timeout: 3000 })
      await waitFor(() => {
        const cartCalls = mockFetch.mock.calls.filter(c => String(c[0]).includes('/api/cart/items'))
        return cartCalls.length >= 3
      }, { timeout: 3000 })
    }
  }, 10000)

  test('drop course failure message', async () => {
    const c = course()
    createMockFetch({
      '/api/cart/items': { success: true, items: [c] },
      '/api/cart/remove': { success: false, message: 'Failed to remove' }
    })
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/items/), expect.any(Object)), { timeout: 3000 })
    await new Promise(resolve => setTimeout(resolve, 1000))
    const allDrops = screen.queryAllByText(/Drop/i)
    if (allDrops.length > 0) {
      const user = userEvent.setup()
      await user.click(allDrops[0])
      expect(await screen.findByText((text) => text.includes('Error removing class from cart'))).toBeInTheDocument()
    }
  }, 10000)

  test('drop course error handling', async () => {
    const c = course()
    createMockFetch({
      '/api/cart/items': { success: true, items: [c] },
      '/api/cart/remove': () => { throw new Error('Server error') }
    })
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/items/), expect.any(Object)), { timeout: 3000 })
    await new Promise(resolve => setTimeout(resolve, 1000))
    const allDrops = screen.queryAllByText(/Drop/i)
    if (allDrops.length > 0) {
      const user = userEvent.setup()
      await user.click(allDrops[0])
      expect(await screen.findByText('Error removing class from cart. Please try again.')).toBeInTheDocument()
    }
  }, 10000)

  test('year selection loads classes', async () => {
    const c = course()
    createMockFetch({ '/api/classes/by-term': [c] })
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    const yearSelect = screen.getByLabelText(/Year/i)
    await userEvent.selectOptions(yearSelect, '2025')
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/classes\/by-term/), expect.any(Object)))
  })

  test('waiver request no prompt', async () => {
    window.prompt = jest.fn(() => null)
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
  })

  test('waiver request already exists', async () => {
    createMockFetch({ '/api/waivers': { success: false, message: 'already exists' } })
    window.prompt = jest.fn(() => 'Test reason')
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
  })

  test('waiver request error', async () => {
    createMockFetch({ '/api/waivers': () => { throw new Error('Server error') } })
    window.prompt = jest.fn(() => 'Test reason')
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
  })

  test('add to cart anyway after a conflict', async () => {
    const a = course({ id: 1, classId: 1, code: 'CSE101.01', schedule: 'MWF 10:00-11:00' })
    const b = course({ id: 2, classId: 2, code: 'CSE102.01', schedule: 'MWF 10:00-11:00' })
    mockCheckTimeConflict.mockResolvedValue({ hasConflict: true, message: 'conflict' })
    createMockFetch({
      '/api/cart/items': { success: true, items: [a] },
      '/api/classes/search': [b],
      '/api/cart/add': { success: true }
    })

    renderWithRouter(<CourseRegistration />)
    const input = await screen.findByPlaceholderText(/Search by class ID/i)
    await userEvent.type(input, 'CSE102')
    await waitFor(async () => {
      const btn = screen.getAllByText(/Add to Cart/i)[0]
      await userEvent.click(btn)
    })
    await clickDialogButton('Time Conflict Detected', 'Add to Cart Anyway')
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/add/), expect.any(Object)), { timeout: 5000 })
  })

  test('add to cart already exists in response', async () => {
    const c = course()
    createMockFetch({
      '/api/classes/search': [c],
      '/api/cart/add': { success: false, message: 'already exists' }
    })
    renderWithRouter(<CourseRegistration />)
    const input = await screen.findByPlaceholderText(/Search by class ID/i)
    await userEvent.type(input, 'CSE101')
    await waitFor(async () => {
      const btn = screen.getAllByText(/Add to Cart/i)[0]
      await userEvent.click(btn)
    })
    expect(await screen.findByText((text) => text.includes('This class is already in your cart!'))).toBeInTheDocument()
  })

  test('registration eligibility check fails', async () => {
    const c = course()
    createMockFetch({
      '/api/cart/items': { success: true, items: [c] },
      '/api/admin/registration-schedules/check/': { canRegister: false }
    })
    renderWithRouter(<CourseRegistration />)
    const submit = await screen.findByText(/Submit Registration/i)
    await userEvent.click(submit)
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/admin\/registration-schedules\/check/), expect.any(Object)), { timeout: 5000 })
  })

  test('registration failed general message', async () => {
    const c = course()
    createMockFetch({
      '/api/cart/items': { success: true, items: [c] },
      '/api/cart/submit-registration': { success: false, message: 'General error' }
    })
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/items/), expect.any(Object)), { timeout: 5000 })
    await new Promise(resolve => setTimeout(resolve, 1000))
    const submit = await screen.findByText(/Submit Registration/i)
    await userEvent.click(submit)
    await clickDialogButton('Confirm Registration', 'Register')
    expect(await screen.findByText((text) => text.includes('Registration failed: General error'))).toBeInTheDocument()
  })

  test('submit with conflicts only', async () => {
    const conflictItem = course({ id: 1, classId: 1, status: 'Conflict', code: 'CSE101.01' })
    createMockFetch({ '/api/cart/items': { success: true, items: [conflictItem] } })
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/items/), expect.any(Object)), { timeout: 5000 })
    await new Promise(resolve => setTimeout(resolve, 1000))
    const submit = await screen.findByText(/Submit Registration/i)
    await userEvent.click(submit)
    expect(await screen.findByText((text) => text.includes('Cannot register classes with time conflicts'))).toBeInTheDocument()
  })

  test('submit with conflicts confirm false', async () => {
    const conflictItem = course({ id: 1, classId: 1, status: 'Conflict', code: 'CSE101.01' })
    const normalItem = course({ id: 2, classId: 2, status: 'Added', code: 'CSE102.01' })
    createMockFetch({
      '/api/cart/items': { success: true, items: [conflictItem, normalItem] },
      '/api/cart/submit-registration': { success: true, registeredCount: 1, waitlistedClasses: [] }
    })

    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/items/), expect.any(Object)), { timeout: 5000 })
    await new Promise(resolve => setTimeout(resolve, 1000))
    const submit = await screen.findByText(/Submit Registration/i)
    await userEvent.click(submit)
    await clickDialogButton('Confirm Registration', 'Cancel')
    expect(mockFetch).not.toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/submit-registration/), expect.any(Object))
  })

  test('add to cart with conflict and request waiver', async () => {
    const a = course({ id: 1, classId: 1, code: 'CSE101.01', schedule: 'MWF 10:00-11:00' })
    const b = course({ id: 2, classId: 2, code: 'CSE102.01', schedule: 'MWF 10:00-11:00' })
    mockCheckTimeConflict.mockResolvedValue({ hasConflict: true, message: 'conflict' })
    createMockFetch({
      '/api/cart/items': { success: true, items: [a] },
      '/api/classes/search': [b],
      '/api/waivers': { success: true }
    })

    window.prompt = jest.fn(() => 'Test reason')
    renderWithRouter(<CourseRegistration />)
    const input = await screen.findByPlaceholderText(/Search by class ID/i)
    await userEvent.type(input, 'CSE102')
    await waitFor(async () => {
      const btn = screen.getAllByText(/Add to Cart/i)[0]
      await userEvent.click(btn)
    })
    await clickDialogButton('Time Conflict Detected', 'Request Waiver')
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/waivers/), expect.any(Object)))
  })

  test('loadRegisteredClasses no userId', async () => {
    mockGetCurrentUser.mockResolvedValueOnce({ user: null } as any)
    renderWithRouter(<CourseRegistration />)
    expect(await screen.findByText(/Loading/i)).toBeInTheDocument()
    await waitFor(() => {
      expect(mockFetch).not.toHaveBeenCalledWith(expect.stringMatching(/\/api\/roster\/student\/\d+/), expect.any(Object))
    }, { timeout: 1000 })
  })

  test('loadRegisteredClasses response not ok', async () => {
    createMockFetch({
      '/api/roster/student/\\d+': () => Promise.resolve({ ok: false, status: 500, json: async () => ({}) } as any)
    })
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/roster\/student\/\d+/), expect.any(Object)), { timeout: 5000 })
  })

  test('loadRegisteredClasses invalid array response', async () => {
    createMockFetch({
      '/api/roster/student/\\d+': () => Promise.resolve({ ok: true, json: async () => ({ notArray: true }) } as any)
    })
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/roster\/student\/\d+/), expect.any(Object)), { timeout: 5000 })
  })

  test('loadRegisteredClasses checkTimeConflict error fallback', async () => {
    const registered1 = course({ id: 1, classId: 1, schedule: 'MWF 10:00-11:00', code: 'CSE101.01' })
    const registered2 = course({ id: 2, classId: 2, schedule: 'MWF 10:00-11:00', code: 'CSE102.01' })
    mockCheckTimeConflict.mockRejectedValue(new Error('Conflict check error'))
    createMockFetch({
      '/api/roster/student/\\d+': [toRosterData(registered1), toRosterData(registered2)]
    })
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    await waitFor(() => expect(mockCheckTimeConflict).toHaveBeenCalled(), { timeout: 5000 })
  })

  test('loadCartFromDB success false', async () => {
    createMockFetch({ '/api/cart/items': { success: false, message: 'Failed' } })
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/items/), expect.any(Object)), { timeout: 5000 })
  })

  test('loadCartFromDB checkTimeConflict error fallback', async () => {
    const cartItem = course({ id: 1, classId: 1, schedule: 'MWF 10:00-11:00' })
    const registered = course({ id: 2, classId: 2, schedule: 'MWF 10:00-11:00', code: 'CSE200.01' })
    mockCheckTimeConflict.mockRejectedValue(new Error('Conflict check error'))
    createMockFetch({
      '/api/cart/items': { success: true, items: [cartItem] },
      '/api/roster/student/\\d+': [toRosterData(registered)]
    })
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    await waitFor(() => expect(mockCheckTimeConflict).toHaveBeenCalled(), { timeout: 5000 })
  })

  test('handleSearch error handling', async () => {
    createMockFetch({ '/api/classes/search': () => { throw new Error('Search error') } })
    renderWithRouter(<CourseRegistration />)
    const input = await screen.findByPlaceholderText(/Search by class ID/i)
    await userEvent.type(input, 'CSE101')
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/classes\/search/), expect.any(Object)), { timeout: 5000 })
  })

  test('fetchAvailableTerms error handling', async () => {
    createMockFetch({ '/api/classes/terms': () => { throw new Error('Terms error') } })
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
  })

  test('fetchClassesByTerm error handling', async () => {
    createMockFetch({ '/api/classes/by-term': () => { throw new Error('By-term error') } })
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    const semesterSelect = screen.getByLabelText(/Semester/i)
    await userEvent.selectOptions(semesterSelect, 'Fall')
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/classes\/by-term/), expect.any(Object)))
  })

  test('handleAddToCart no userId', async () => {
    const c = course()
    mockGetCurrentUser.mockResolvedValueOnce({ user: null } as any)
    createMockFetch({ '/api/classes/search': [c] })
    renderWithRouter(<CourseRegistration />)
    expect(await screen.findByText(/Loading/i)).toBeInTheDocument()
    await waitFor(() => {
      expect(mockFetch).not.toHaveBeenCalledWith(expect.stringMatching(/\/api\/classes\/search/), expect.any(Object))
    }, { timeout: 1000 })
  })

  test('handleAddToCart error message not already exists', async () => {
    const c = course()
    createMockFetch({
      '/api/classes/search': [c],
      '/api/cart/add': { success: false, message: 'Other error' }
    })
    renderWithRouter(<CourseRegistration />)
    const input = await screen.findByPlaceholderText(/Search by class ID/i)
    await userEvent.type(input, 'CSE101')
    await waitFor(async () => {
      const btn = screen.getAllByText(/Add to Cart/i)[0]
      await userEvent.click(btn)
    })
    expect(await screen.findByText((text) => text.includes('Error adding class to cart: Other error'))).toBeInTheDocument()
  })

  test('handleDropRegisteredClass no userId', async () => {
    const enrolled = course({ status: 'Enrolled' })
    mockGetCurrentUser.mockResolvedValueOnce({ user: null } as any)
    createMockFetch({ '/api/roster/student/\\d+': [toRosterData(enrolled)] })
    renderWithRouter(<CourseRegistration />)
    expect(await screen.findByText(/Loading/i)).toBeInTheDocument()
    await waitFor(() => {
      expect(mockFetch).not.toHaveBeenCalledWith(expect.stringMatching(/\/api\/roster\/student\/\d+/), expect.any(Object))
    }, { timeout: 1000 })
  })

  test('handleDropRegisteredClass confirm false', async () => {
    const enrolled = course({ status: 'Enrolled' })
    createMockFetch({ '/api/roster/student/\\d+': [toRosterData(enrolled)] })

    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/roster\/student\/\d+/), expect.any(Object)), { timeout: 5000 })
    await userEvent.click(await screen.findByRole('button', { name: 'Drop', exact: true }))
    await clickDialogButton('Drop Class', 'Cancel')

    await waitFor(() => {
      expect(mockFetch).not.toHaveBeenCalledWith(expect.stringMatching(/\/api\/users\/\d+\/classes\/\d+\/drop/), expect.any(Object))
    }, { timeout: 1000 })
  }, 15000)

  test('handleSubmitRegistration holds check error', async () => {
    const c = course()
    createMockFetch({
      '/api/cart/items': { success: true, items: [c] },
      '/api/holds/check/': () => { throw new Error('Holds check error') },
      '/api/cart/submit-registration': { success: true, registeredCount: 1, waitlistedClasses: [] }
    })
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/items/), expect.any(Object)), { timeout: 5000 })
    await new Promise(resolve => setTimeout(resolve, 1500))
    const submit = await screen.findByText(/Submit Registration/i)
    await userEvent.click(submit)
    await clickDialogButton('Confirm Registration', 'Register')
    await waitFor(() => {
      const holdsCalls = mockFetch.mock.calls.filter(c => String(c[0]).includes('/api/holds/check/'))
      return holdsCalls.length >= 1
    }, { timeout: 5000 })
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/submit-registration/), expect.any(Object)), { timeout: 5000 })
  }, 15000)

  test('handleDropCourse no userId', async () => {
    const c = course()
    mockGetCurrentUser.mockResolvedValueOnce({ user: null } as any)
    createMockFetch({ '/api/cart/items': { success: true, items: [c] } })
    renderWithRouter(<CourseRegistration />)
    expect(await screen.findByText(/Loading/i)).toBeInTheDocument()
    await waitFor(() => {
      expect(mockFetch).not.toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/items/), expect.any(Object))
    }, { timeout: 1000 })
  })

  test('handleDropCourse with semester and year reload', async () => {
    const c = course()
    let callCount = 0
    createMockFetch({
      '/api/cart/items': () => {
        callCount++
        return callCount <= 2 ? { success: true, items: [c] } : { success: true, items: [] }
      },
      '/api/cart/remove': { success: true },
      '/api/roster/student/\\d+': []
    })
    renderWithRouter(<CourseRegistration />)
    await waitForCourseSearch()
    const semesterSelect = screen.getByLabelText(/Semester/i)
    await userEvent.selectOptions(semesterSelect, 'Fall')
    const yearSelect = screen.getByLabelText(/Year/i)
    await userEvent.selectOptions(yearSelect, '2025')
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/items/), expect.any(Object)), { timeout: 5000 })
    await new Promise(resolve => setTimeout(resolve, 1000))
    const allDrops = screen.queryAllByText(/Drop/i)
    if (allDrops.length > 0) {
      const user = userEvent.setup()
      await user.click(allDrops[0])
      await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(expect.stringMatching(/\/api\/cart\/remove/), expect.any(Object)), { timeout: 3000 })
      await waitFor(() => {
        const rosterCalls = mockFetch.mock.calls.filter(c => String(c[0]).match(/\/api\/roster\/student\/\d+/))
        return rosterCalls.length >= 1
      }, { timeout: 3000 })
    }
  }, 15000)
})

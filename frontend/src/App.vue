<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { api } from './api'

type User = { id: string; name: string; phoneNumber: string; role: string }
type Account = {
  id: string
  ownerId: string
  provider: string
  accountType: string
  accountAlias: string
  maskedReference: string
  availableBalance: number
  verified: boolean
  smartPayEnabled: boolean
  smartSettlementEnabled: boolean
}
type Provider = {
  code: string
  name: string
  successRate: number
  healthScore: number
  costScore: number
  latencyScore: number
  latencyMs: number
  mode: string
}
type Candidate = {
  sourceAccountId: string
  sourceProvider: string
  routeProvider: string
  destinationAccountId: string
  destinationProvider: string
  reliabilityScore: number
  successProbability: number
  costScore: number
  latencyScore: number
  finalScore: number
  selected: boolean
  explanation: string
}
type PaymentAttempt = {
  attemptNumber: number
  provider: string
  status: string
  errorCode: string | null
  latencyMs: number
  attemptedAt: string
}
type Payment = {
  id: string
  merchantReference: string
  amount: number
  currency: string
  senderId: string
  receiverId: string
  sourceAccountId: string | null
  destinationAccountId: string | null
  routeProvider: string | null
  status: string
  attempts: PaymentAttempt[]
  candidates: Candidate[]
  createdAt: string
}
type RouteNodeId = 'source' | 'scoring' | 'ranking' | 'settlement' | 'execution'

const users = ref<User[]>([])
const providers = ref<Provider[]>([])
const payments = ref<Payment[]>([])
const senderAccounts = ref<Account[]>([])
const receiverAccounts = ref<Account[]>([])
const result = ref<Payment | null>(null)
const busy = ref(false)
const statusMessage = ref('Load the judge demo to create a ready-made merchant checkout scenario.')
const activePanel = ref<'presenter' | 'showcase' | 'builder'>('presenter')
const activeSlide = ref(0)
const elapsedSeconds = ref(0)
const timerRunning = ref(false)
const timerHandle = ref<number | null>(null)
const baselineRoute = ref('Not captured yet')
const outageRoute = ref('Not captured yet')
const routingLens = ref<'Balanced' | 'Approval' | 'Cost' | 'Latency'>('Balanced')
const activeRoutingNode = ref<RouteNodeId>('scoring')
const isFullscreen = ref(false)

const userForm = reactive({ name: '', phoneNumber: '', role: 'INDIVIDUAL' })
const selectedAccountOwner = ref('')
const accountForm = reactive({
  provider: 'TELEBIRR',
  accountType: 'WALLET',
  accountAlias: '',
  accountReference: '',
  availableBalance: 0,
  smartPayEnabled: true,
  smartSettlementEnabled: false
})

const paymentForm = reactive({
  merchantReference: 'BUNA-ORDER-3001',
  amount: 2500,
  currency: 'ETB',
  senderId: '',
  receiverId: '',
  selectedSourceAccountId: '',
  selectedDestinationAccountId: '',
  smartPay: true,
  smartSettlement: true
})

const demoCustomerPhone = '+251911000301'
const demoMerchantPhone = '+251911000401'

const senderUsers = computed(() => users.value)
const receiverUsers = computed(() => users.value)
const latestPayment = computed(() => result.value ?? payments.value[0] ?? null)
const selectedCandidate = computed(() => latestPayment.value?.candidates.find((candidate) => candidate.selected) ?? null)
const rankedCandidates = computed(() => latestPayment.value?.candidates.slice(0, 5) ?? [])
const providerHealth = computed(() => {
  const total = providers.value.length || 1
  const healthy = providers.value.filter((provider) => provider.mode === 'HEALTHY').length
  return Math.round((healthy / total) * 100)
})
const approvalRate = computed(() => {
  if (!payments.value.length) return 0
  const approved = payments.value.filter((payment) => payment.status === 'SUCCEEDED').length
  return Math.round((approved / payments.value.length) * 100)
})
const averageLatency = computed(() => {
  const attempts = payments.value.flatMap((payment) => payment.attempts)
  if (!attempts.length) return 0
  return Math.round(attempts.reduce((sum, attempt) => sum + attempt.latencyMs, 0) / attempts.length)
})
const activeOutages = computed(() => providers.value.filter((provider) => provider.mode !== 'HEALTHY').length)
const impactCards = computed(() => [
  {
    label: 'Approval lift',
    value: latestPayment.value ? `${Math.max(8, Math.round((selectedCandidate.value?.successProbability ?? 0.88) * 22))}%` : '18%',
    detail: 'from routing away from weak providers'
  },
  {
    label: 'Cost score',
    value: selectedCandidate.value ? `${Math.round(selectedCandidate.value.costScore * 100)}/100` : '82/100',
    detail: 'balances fees with reliability'
  },
  {
    label: 'Failover window',
    value: latestPayment.value?.attempts.length ? `${latestPayment.value.attempts.length} tries` : '<3 tries',
    detail: 'automatic retry across eligible rails'
  },
  {
    label: 'Risk controls',
    value: '4 checks',
    detail: 'idempotency, funds, verified accounts, provider health'
  }
])
const presenterSlides = [
  {
    minute: '0:00',
    title: 'Open with the pain',
    prompt: 'Ask: What happens to a merchant when one payment rail silently slows down during peak checkout?',
    proof: 'Fragmented rails create failed payments, manual switching, and poor visibility.',
    actionLabel: 'Load the live scenario',
    action: 'load-demo',
    presenterNote: 'Introduce Aster Buna Merchant and Dawit Customer. This is not a mock slide; it creates users and linked accounts through the API.'
  },
  {
    minute: '1:00',
    title: 'Show intelligent routing',
    prompt: 'Ask: Should the customer choose the rail, or should the gateway choose the best path automatically?',
    proof: 'PayFlow scores source account, route provider, settlement account, success probability, health, cost, and latency.',
    actionLabel: 'Run smart route',
    action: 'smart-route',
    presenterNote: 'Point to the winning corridor, score bars, and selected path. Explain why the top route won.'
  },
  {
    minute: '2:10',
    title: 'Break a provider live',
    prompt: 'Ask: What should happen if the best provider goes down after checkout starts?',
    proof: 'Unavailable providers are excluded and the payment retries across eligible alternatives.',
    actionLabel: 'Trigger outage and reroute',
    action: 'outage',
    presenterNote: 'Click once, then show provider health and attempts. This is the resilience moment judges remember.'
  },
  {
    minute: '3:20',
    title: 'Prove risk controls',
    prompt: 'Ask: How do we avoid duplicate charges and spending the same balance twice?',
    proof: 'The gateway enforces idempotency, verified accounts, balance checks, debit-on-success, and provider health gates.',
    actionLabel: 'Restore rails',
    action: 'restore',
    presenterNote: 'Connect the safeguards to trust, compliance readiness, and production viability.'
  },
  {
    minute: '4:20',
    title: 'Close with impact',
    prompt: 'Ask: Where does this create value for banks, wallets, gateways, and merchants?',
    proof: 'Higher approvals, lower cost routing, faster outage recovery, and API-first integration across payment ecosystems.',
    actionLabel: 'Show impact',
    action: 'impact',
    presenterNote: 'End on business value: PayFlow is a routing intelligence layer, not just another checkout button.'
  }
] as const
const currentSlide = computed(() => presenterSlides[activeSlide.value])
const presentationProgress = computed(() => Math.round(((activeSlide.value + 1) / presenterSlides.length) * 100))
const formattedElapsed = computed(() => {
  const minutes = Math.floor(elapsedSeconds.value / 60).toString()
  const seconds = (elapsedSeconds.value % 60).toString().padStart(2, '0')
  return `${minutes}:${seconds}`
})
const routingNodes = computed(() => [
  {
    id: 'source' as RouteNodeId,
    label: 'Funding',
    title: selectedCandidate.value?.sourceProvider ?? 'Customer accounts',
    meta: `${senderAccounts.value.length || 3} eligible rails`,
    status: selectedCandidate.value ? 'selected' : 'ready'
  },
  {
    id: 'scoring' as RouteNodeId,
    label: 'AI scoring',
    title: routingLens.value,
    meta: 'multi-factor model',
    status: busy.value ? 'active' : 'ready'
  },
  {
    id: 'ranking' as RouteNodeId,
    label: 'Provider',
    title: selectedCandidate.value?.routeProvider ?? 'Ranked routes',
    meta: activeOutages.value ? `${activeOutages.value} issue detected` : 'healthy network',
    status: activeOutages.value ? 'warning' : 'ready'
  },
  {
    id: 'settlement' as RouteNodeId,
    label: 'Settlement',
    title: selectedCandidate.value?.destinationProvider ?? 'Merchant accounts',
    meta: `${receiverAccounts.value.length || 2} settlement rails`,
    status: selectedCandidate.value ? 'selected' : 'ready'
  },
  {
    id: 'execution' as RouteNodeId,
    label: 'Execution',
    title: latestPayment.value?.status ?? 'Awaiting payment',
    meta: `${latestPayment.value?.attempts.length ?? 0} attempt${latestPayment.value?.attempts.length === 1 ? '' : 's'}`,
    status: latestPayment.value?.status === 'SUCCEEDED' ? 'selected' : 'ready'
  }
])
const routingWeights = computed(() => {
  const presets = {
    Balanced: [
      ['Success', 35],
      ['Health', 25],
      ['Cost', 20],
      ['Latency', 15],
      ['Affinity', 5]
    ],
    Approval: [
      ['Success', 46],
      ['Health', 28],
      ['Cost', 10],
      ['Latency', 10],
      ['Affinity', 6]
    ],
    Cost: [
      ['Success', 26],
      ['Health', 18],
      ['Cost', 38],
      ['Latency', 12],
      ['Affinity', 6]
    ],
    Latency: [
      ['Success', 28],
      ['Health', 18],
      ['Cost', 14],
      ['Latency', 34],
      ['Affinity', 6]
    ]
  } satisfies Record<typeof routingLens.value, Array<[string, number]>>
  return presets[routingLens.value]
})
const activeRoutingDetail = computed(() => {
  const candidate = selectedCandidate.value
  if (activeRoutingNode.value === 'source') {
    return {
      title: 'Funding rail selection',
      body: candidate
        ? `${candidate.sourceProvider} was selected from ${senderAccounts.value.length} customer rails because it was verified, fundable, and eligible for Smart Pay.`
        : 'PayFlow first filters customer accounts by verification, Smart Pay eligibility, and available balance.',
      metric: candidate ? `${accountName(candidate.sourceAccountId)}` : `${senderAccounts.value.length || 3} candidate accounts`
    }
  }
  if (activeRoutingNode.value === 'scoring') {
    return {
      title: 'Decision model',
      body: `The ${routingLens.value} lens changes what you emphasize in the pitch while the engine still balances success probability, health, cost, latency, and account affinity.`,
      metric: `${routingWeights.value[0][0]} ${routingWeights.value[0][1]}%`
    }
  }
  if (activeRoutingNode.value === 'ranking') {
    return {
      title: 'Provider ranking',
      body: candidate
        ? `${candidate.routeProvider} is leading with ${Math.round(candidate.finalScore * 100)}/100 after unavailable providers are excluded.`
        : 'Run Smart Route to rank providers and highlight the winning rail.',
      metric: candidate ? `${Math.round(candidate.successProbability * 100)}% approval probability` : `${providers.value.length || 3} providers`
    }
  }
  if (activeRoutingNode.value === 'settlement') {
    return {
      title: 'Smart settlement',
      body: candidate
        ? `${candidate.destinationProvider} is selected as the receiver-side settlement account for this corridor.`
        : 'The receiver can link multiple settlement rails and let PayFlow choose the best destination.',
      metric: candidate ? accountName(candidate.destinationAccountId) : `${receiverAccounts.value.length || 2} settlement options`
    }
  }
  return {
    title: 'Payment execution',
    body: latestPayment.value
      ? `The gateway executed ${latestPayment.value.attempts.length} attempt${latestPayment.value.attempts.length === 1 ? '' : 's'} and returned ${latestPayment.value.status}.`
      : 'Once a route is selected, PayFlow charges through the provider simulator and records the attempts.',
    metric: latestPayment.value?.status ?? 'Ready'
  }
})

async function loadAll() {
  const [u, p, tx] = await Promise.all([
    api.get('/users'),
    api.get('/providers'),
    api.get('/payments')
  ])
  users.value = u.data
  providers.value = p.data
  payments.value = tx.data
  await refreshAccounts()
}

async function createUser() {
  await api.post('/users', userForm)
  userForm.name = ''
  userForm.phoneNumber = ''
  await loadAll()
}

async function linkAccount() {
  if (!selectedAccountOwner.value) return
  await api.post(`/users/${selectedAccountOwner.value}/accounts`, accountForm)
  accountForm.accountAlias = ''
  accountForm.accountReference = ''
  accountForm.availableBalance = 0
  await refreshAccounts()
}

async function refreshAccounts() {
  senderAccounts.value = paymentForm.senderId
    ? (await api.get(`/users/${paymentForm.senderId}/accounts`)).data
    : []
  receiverAccounts.value = paymentForm.receiverId
    ? (await api.get(`/users/${paymentForm.receiverId}/accounts`)).data
    : []
}

async function createPayment(reference = paymentForm.merchantReference) {
  const payload = { ...paymentForm, merchantReference: reference }
  const { data } = await api.post('/payments', payload, {
    headers: { 'Idempotency-Key': `${reference}-${Date.now()}` }
  })
  result.value = data
  await loadAll()
}

async function setProviderMode(code: string, mode: string) {
  await api.post(`/providers/${code}/mode`, { mode })
  await loadAll()
}

async function getOrCreateUser(name: string, phoneNumber: string, role: string) {
  const existing = users.value.find((user) => user.phoneNumber === phoneNumber)
  if (existing) return existing
  const { data } = await api.post('/users', { name, phoneNumber, role })
  return data as User
}

async function getOrCreateAccount(owner: User, account: Partial<Account> & {
  provider: string
  accountType: string
  accountAlias: string
  accountReference: string
  availableBalance: number
  smartPayEnabled: boolean
  smartSettlementEnabled: boolean
}) {
  const { data: existingAccounts } = await api.get(`/users/${owner.id}/accounts`)
  const existing = (existingAccounts as Account[]).find((item) => item.accountAlias === account.accountAlias)
  if (existing) return existing
  const { data } = await api.post(`/users/${owner.id}/accounts`, account)
  return data as Account
}

async function loadJudgeDemo() {
  busy.value = true
  statusMessage.value = 'Creating merchant, customer, linked payment rails, and healthy provider baseline...'
  try {
    await loadAll()
    await Promise.all(providers.value.map((provider) => setProviderMode(provider.code, 'HEALTHY')))
    await loadAll()

    const customer = await getOrCreateUser('Dawit Retail Customer', demoCustomerPhone, 'INDIVIDUAL')
    const merchant = await getOrCreateUser('Aster Buna Merchant', demoMerchantPhone, 'MERCHANT')

    await Promise.all([
      getOrCreateAccount(customer, {
        provider: 'TELEBIRR',
        accountType: 'WALLET',
        accountAlias: 'Telebirr daily wallet',
        accountReference: 'TB-DAWIT-3001',
        availableBalance: 9000,
        smartPayEnabled: true,
        smartSettlementEnabled: false
      }),
      getOrCreateAccount(customer, {
        provider: 'CBE',
        accountType: 'BANK_ACCOUNT',
        accountAlias: 'CBE salary account',
        accountReference: 'CBE-DAWIT-9001',
        availableBalance: 64000,
        smartPayEnabled: true,
        smartSettlementEnabled: false
      }),
      getOrCreateAccount(customer, {
        provider: 'MPESA',
        accountType: 'WALLET',
        accountAlias: 'M-Pesa backup wallet',
        accountReference: 'MP-DAWIT-4401',
        availableBalance: 1200,
        smartPayEnabled: true,
        smartSettlementEnabled: false
      }),
      getOrCreateAccount(merchant, {
        provider: 'CBE',
        accountType: 'MERCHANT_ACCOUNT',
        accountAlias: 'CBE merchant settlement',
        accountReference: 'CBE-ASTER-7001',
        availableBalance: 0,
        smartPayEnabled: false,
        smartSettlementEnabled: true
      }),
      getOrCreateAccount(merchant, {
        provider: 'TELEBIRR',
        accountType: 'MERCHANT_ACCOUNT',
        accountAlias: 'Telebirr merchant wallet',
        accountReference: 'TB-ASTER-8001',
        availableBalance: 0,
        smartPayEnabled: false,
        smartSettlementEnabled: true
      })
    ])

    paymentForm.senderId = customer.id
    paymentForm.receiverId = merchant.id
    paymentForm.merchantReference = `BUNA-ORDER-${Math.floor(Date.now() / 1000)}`
    paymentForm.amount = 2500
    paymentForm.currency = 'ETB'
    paymentForm.smartPay = true
    paymentForm.smartSettlement = true
    paymentForm.selectedSourceAccountId = ''
    paymentForm.selectedDestinationAccountId = ''
    await refreshAccounts()
    statusMessage.value = 'Judge demo loaded. Run Smart Route to show AI ranking and settlement choice.'
  } finally {
    busy.value = false
  }
}

async function runSmartRoute() {
  if (!paymentForm.senderId || !paymentForm.receiverId) await loadJudgeDemo()
  busy.value = true
  statusMessage.value = 'Scoring source accounts, payment rails, destination accounts, provider health, cost, and latency...'
  try {
    const reference = `BUNA-SMART-${Math.floor(Date.now() / 1000)}`
    paymentForm.merchantReference = reference
    await createPayment(reference)
    baselineRoute.value = routeLabel(result.value)
    statusMessage.value = 'Smart route completed. The ranked corridor table now explains why the winning path was selected.'
  } finally {
    busy.value = false
  }
}

async function simulateOutageAndReroute() {
  if (!paymentForm.senderId || !paymentForm.receiverId) await loadJudgeDemo()
  busy.value = true
  statusMessage.value = 'Simulating a provider outage, then rerunning the transaction through the failover engine...'
  try {
    await setProviderMode('ROUTE_A', 'UNAVAILABLE')
    const reference = `BUNA-FAILOVER-${Math.floor(Date.now() / 1000)}`
    paymentForm.merchantReference = reference
    await createPayment(reference)
    outageRoute.value = routeLabel(result.value)
    statusMessage.value = 'Outage scenario complete. The route engine excluded unavailable rails and retried eligible alternatives.'
  } finally {
    busy.value = false
  }
}

async function takeProviderDownAndReroute(code: string) {
  if (!paymentForm.senderId || !paymentForm.receiverId) await loadJudgeDemo()
  if (!latestPayment.value) await runSmartRoute()
  baselineRoute.value = routeLabel(latestPayment.value)
  busy.value = true
  statusMessage.value = `Taking ${code} down live, then rerouting the same merchant checkout through available rails...`
  try {
    await setProviderMode(code, 'UNAVAILABLE')
    const reference = `BUNA-${code}-DOWN-${Math.floor(Date.now() / 1000)}`
    paymentForm.merchantReference = reference
    await createPayment(reference)
    outageRoute.value = routeLabel(result.value)
    statusMessage.value = `${code} is unavailable. PayFlow reranked the corridor and selected ${outageRoute.value}.`
  } finally {
    busy.value = false
  }
}

async function setProviderStress(code: string, mode: string) {
  busy.value = true
  statusMessage.value = `Changing ${code} to ${mode}; watch the provider health and ranking evidence update.`
  try {
    await setProviderMode(code, mode)
  } finally {
    busy.value = false
  }
}

async function restoreProviderHealth() {
  busy.value = true
  try {
    await Promise.all(providers.value.map((provider) => setProviderMode(provider.code, 'HEALTHY')))
    statusMessage.value = 'All route providers restored to their healthy baseline metrics.'
  } finally {
    busy.value = false
  }
}

function startPresentation() {
  activePanel.value = 'presenter'
  activeSlide.value = 0
  elapsedSeconds.value = 0
  timerRunning.value = true
  if (timerHandle.value !== null) window.clearInterval(timerHandle.value)
  timerHandle.value = window.setInterval(() => {
    elapsedSeconds.value += 1
  }, 1000)
}

function toggleTimer() {
  if (timerRunning.value) {
    timerRunning.value = false
    if (timerHandle.value !== null) window.clearInterval(timerHandle.value)
    timerHandle.value = null
    return
  }
  timerRunning.value = true
  timerHandle.value = window.setInterval(() => {
    elapsedSeconds.value += 1
  }, 1000)
}

async function toggleFullscreen() {
  activePanel.value = 'presenter'
  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen()
      isFullscreen.value = false
      return
    }

    await document.documentElement.requestFullscreen()
    isFullscreen.value = true
  } catch {
    isFullscreen.value = !isFullscreen.value
  }
}

function resetPresentation() {
  activeSlide.value = 0
  elapsedSeconds.value = 0
  baselineRoute.value = 'Not captured yet'
  outageRoute.value = 'Not captured yet'
  routingLens.value = 'Balanced'
  statusMessage.value = 'Presenter mode reset. Start with the merchant pain, then load the live demo.'
}

function setRoutingLens(lens: string) {
  routingLens.value = lens as typeof routingLens.value
  statusMessage.value = `Scoring lens set to ${lens}. Now run Smart Route and explain how PayFlow still balances all route factors.`
}

function setActiveRoutingNode(id: RouteNodeId) {
  activeRoutingNode.value = id
}

function nextSlide() {
  activeSlide.value = Math.min(activeSlide.value + 1, presenterSlides.length - 1)
}

function previousSlide() {
  activeSlide.value = Math.max(activeSlide.value - 1, 0)
}

async function runPresenterAction() {
  const action = currentSlide.value.action
  if (action === 'load-demo') await loadJudgeDemo()
  if (action === 'smart-route') await runSmartRoute()
  if (action === 'outage') await simulateOutageAndReroute()
  if (action === 'restore') await restoreProviderHealth()
  if (action === 'impact') statusMessage.value = 'Close on impact: higher approvals, resilient checkout, API-first orchestration, and safer settlement.'
}

function formatPercent(value: number) {
  return `${Math.round(value * 100)}%`
}

function formatMoney(value: number) {
  return new Intl.NumberFormat('en-ET', { maximumFractionDigits: 0 }).format(value)
}

function accountName(id: string | null) {
  if (!id) return 'None'
  return [...senderAccounts.value, ...receiverAccounts.value].find((account) => account.id === id)?.accountAlias ?? id
}

function routeLabel(payment: Payment | null) {
  const candidate = payment?.candidates.find((item) => item.selected)
  if (candidate) return `${candidate.sourceProvider} → ${candidate.routeProvider} → ${candidate.destinationProvider}`
  if (payment?.routeProvider) return `${payment.sourceAccountId ?? 'source'} → ${payment.routeProvider} → ${payment.destinationAccountId ?? 'destination'}`
  return 'No route selected'
}

function providerModeClass(mode: string) {
  return mode.toLowerCase()
}

function syncFullscreenState() {
  isFullscreen.value = Boolean(document.fullscreenElement)
}

onUnmounted(() => {
  if (timerHandle.value !== null) window.clearInterval(timerHandle.value)
  document.removeEventListener('fullscreenchange', syncFullscreenState)
})

onMounted(() => {
  document.addEventListener('fullscreenchange', syncFullscreenState)
  loadAll()
})
</script>

<template>
  <div class="app-shell" :class="{ 'presenter-fullscreen': isFullscreen && activePanel === 'presenter' }">
    <header class="hero">
      <div>
        <p class="eyebrow">Kifiya Inspire Hackathon V4 2026 · Intelligent Payment Routing</p>
        <h1>PayFlow AI payment command center</h1>
        <p class="hero-copy">
          A live gateway demo that routes Ethiopian merchant payments across banks, wallets, and payment rails using
          provider health, success probability, cost, latency, account eligibility, and automatic failover.
        </p>
        <div class="hero-actions">
          <button class="primary" :disabled="busy" @click="startPresentation">Start 5-minute pitch</button>
          <button :disabled="busy" @click="loadJudgeDemo">Load judge demo</button>
          <button :disabled="busy" @click="runSmartRoute">Run smart route</button>
          <button :disabled="busy" @click="simulateOutageAndReroute">Simulate outage</button>
        </div>
      </div>

      <div class="routing-board" aria-label="Payment route visualization">
        <div class="route-node source">
          <span>Customer</span>
          <strong>{{ senderAccounts.length || 3 }} funding rails</strong>
        </div>
        <div class="route-lanes">
          <div v-for="provider in providers" :key="provider.code" class="lane" :class="providerModeClass(provider.mode)">
            <span>{{ provider.code }}</span>
            <strong>{{ provider.mode }}</strong>
          </div>
          <div class="payment-pulse" :class="{ active: busy || latestPayment }"></div>
        </div>
        <div class="route-node destination">
          <span>Merchant</span>
          <strong>{{ receiverAccounts.length || 2 }} settlement rails</strong>
        </div>
      </div>
    </header>

    <main>
      <section class="status-strip">
        <div>
          <span>Demo state</span>
          <strong>{{ statusMessage }}</strong>
        </div>
        <div>
          <span>Provider health</span>
          <strong>{{ providerHealth }}%</strong>
        </div>
        <div>
          <span>Approval rate</span>
          <strong>{{ approvalRate }}%</strong>
        </div>
        <div>
          <span>Avg latency</span>
          <strong>{{ averageLatency }} ms</strong>
        </div>
      </section>

      <nav class="tabs" aria-label="Demo views">
        <button :class="{ active: activePanel === 'presenter' }" @click="activePanel = 'presenter'">Presenter</button>
        <button :class="{ active: activePanel === 'showcase' }" @click="activePanel = 'showcase'">Showcase</button>
        <button :class="{ active: activePanel === 'builder' }" @click="activePanel = 'builder'">API builder</button>
      </nav>

      <template v-if="activePanel === 'presenter'">
        <section class="presenter-stage">
          <aside class="runway panel">
            <div class="timer-card">
              <span>Pitch timer</span>
              <strong>{{ formattedElapsed }}</strong>
              <div class="timer-actions">
                <button class="small" @click="toggleTimer">{{ timerRunning ? 'Pause' : 'Resume' }}</button>
                <button class="small" @click="resetPresentation">Reset</button>
                <button class="small" @click="toggleFullscreen">{{ isFullscreen ? 'Exit full screen' : 'Full screen' }}</button>
              </div>
            </div>

            <div class="progress-track">
              <span :style="{ width: `${presentationProgress}%` }"></span>
            </div>

            <button
              v-for="(slide, index) in presenterSlides"
              :key="slide.title"
              class="runway-step"
              :class="{ active: activeSlide === index, done: activeSlide > index }"
              @click="activeSlide = index"
            >
              <span>{{ slide.minute }}</span>
              <strong>{{ slide.title }}</strong>
            </button>
          </aside>

          <section class="presenter-card panel">
            <div class="panel-heading presenter-heading">
              <div>
                <p class="eyebrow">5-minute presenter mode</p>
                <h2>{{ currentSlide.title }}</h2>
              </div>
              <span class="slide-count">Step {{ activeSlide + 1 }} / {{ presenterSlides.length }}</span>
            </div>

            <div class="talk-track">
              <article>
                <span>Ask the judges</span>
                <strong>{{ currentSlide.prompt }}</strong>
              </article>
              <article>
                <span>Evidence to show</span>
                <strong>{{ currentSlide.proof }}</strong>
              </article>
              <article>
                <span>Your presenter note</span>
                <strong>{{ currentSlide.presenterNote }}</strong>
              </article>
            </div>

            <section v-if="activeSlide === 1" class="route-studio" aria-label="Interactive intelligent routing simulator">
              <div class="route-map">
                <button
                  v-for="node in routingNodes"
                  :key="node.id"
                  class="route-map-node"
                  :class="[node.status, { active: activeRoutingNode === node.id }]"
                  @click="setActiveRoutingNode(node.id)"
                >
                  <span>{{ node.label }}</span>
                  <strong>{{ node.title }}</strong>
                  <small>{{ node.meta }}</small>
                </button>
              </div>

              <div class="route-inspector">
                <article class="route-detail">
                  <span>Selected stage</span>
                  <strong>{{ activeRoutingDetail.title }}</strong>
                  <p>{{ activeRoutingDetail.body }}</p>
                  <em>{{ activeRoutingDetail.metric }}</em>
                </article>

                <article class="weight-panel">
                  <div>
                    <span>Current scoring lens</span>
                    <strong>{{ routingLens }}</strong>
                  </div>
                  <label v-for="weight in routingWeights" :key="weight[0]">
                    {{ weight[0] }}
                    <span><i :style="{ width: `${weight[1]}%` }"></i></span>
                    <b>{{ weight[1] }}%</b>
                  </label>
                </article>
              </div>
            </section>

            <div class="stage-interaction">
              <div class="interaction-copy">
                <span>Live interaction for this stage</span>
                <strong v-if="activeSlide === 0">Create the exact customer, merchant, and linked rails the judges will watch.</strong>
                <strong v-else-if="activeSlide === 1">Pick a scoring lens, run the route, then point at the flow chart and ranked corridor.</strong>
                <strong v-else-if="activeSlide === 2">Choose any provider to take down live and compare route before versus after.</strong>
                <strong v-else-if="activeSlide === 3">Stress a rail, restore it, and connect the controls to trust and compliance.</strong>
                <strong v-else>Invite judges to choose the value lens, then close on measurable impact.</strong>
              </div>

              <div v-if="activeSlide === 0" class="interaction-controls">
                <button class="primary" :disabled="busy" @click="loadJudgeDemo">Load users and accounts</button>
                <button :disabled="busy" @click="restoreProviderHealth">Start all providers healthy</button>
              </div>

              <div v-else-if="activeSlide === 1" class="interaction-controls">
                <button
                  v-for="lens in ['Balanced', 'Approval', 'Cost', 'Latency']"
                  :key="lens"
                  :class="{ active: routingLens === lens }"
                  @click="setRoutingLens(lens)"
                >
                  {{ lens }}
                </button>
                <button class="primary" :disabled="busy" @click="runSmartRoute">Score route</button>
              </div>

              <div v-else-if="activeSlide === 2" class="interaction-controls provider-breakers">
                <button v-for="provider in providers" :key="provider.code" :disabled="busy" @click="takeProviderDownAndReroute(provider.code)">
                  Take {{ provider.code }} down
                </button>
              </div>

              <div v-else-if="activeSlide === 3" class="interaction-controls provider-breakers">
                <button v-for="provider in providers" :key="provider.code" :disabled="busy" @click="setProviderStress(provider.code, 'UNSTABLE')">
                  Stress {{ provider.code }}
                </button>
                <button class="primary" :disabled="busy" @click="restoreProviderHealth">Restore trust baseline</button>
              </div>

              <div v-else class="interaction-controls">
                <button @click="statusMessage = 'Impact lens: merchants see fewer failed payments and less manual provider switching.'">Merchant value</button>
                <button @click="statusMessage = 'Impact lens: banks, wallets, and gateways can expose smarter payment orchestration APIs.'">Ecosystem value</button>
                <button class="primary" @click="statusMessage = 'Final close: PayFlow AI is the intelligence layer for resilient Ethiopian payment infrastructure.'">Final close</button>
              </div>
            </div>

            <div class="presenter-actions">
              <button @click="previousSlide" :disabled="activeSlide === 0">Previous</button>
              <button class="primary" :disabled="busy" @click="runPresenterAction">{{ currentSlide.actionLabel }}</button>
              <button @click="nextSlide" :disabled="activeSlide === presenterSlides.length - 1">Next</button>
            </div>

            <div class="demo-evidence">
              <article class="evidence-card">
                <span>Current route</span>
                <strong>
                  <template v-if="selectedCandidate">
                    {{ selectedCandidate.sourceProvider }} → {{ selectedCandidate.routeProvider }} → {{ selectedCandidate.destinationProvider }}
                  </template>
                  <template v-else>Not scored yet</template>
                </strong>
                <small>{{ selectedCandidate ? `${Math.round(selectedCandidate.finalScore * 100)}/100 route score` : 'Run Smart Route during step 2.' }}</small>
              </article>

              <article class="evidence-card route-change-card">
                <span>Route change</span>
                <strong>{{ baselineRoute }}</strong>
                <small>After outage: {{ outageRoute }}</small>
              </article>

              <article class="evidence-card">
                <span>Outage pressure</span>
                <strong>{{ activeOutages }} active issue{{ activeOutages === 1 ? '' : 's' }}</strong>
                <small>{{ activeOutages ? 'Provider health is degrading live.' : 'All route providers are healthy.' }}</small>
              </article>

              <article class="evidence-card">
                <span>Attempts</span>
                <strong>{{ latestPayment?.attempts.length ?? 0 }}</strong>
                <small>{{ latestPayment ? latestPayment.status : 'No payment processed yet.' }}</small>
              </article>
            </div>
          </section>

          <aside class="judge-wall panel">
            <p class="eyebrow">Interactive judge moments</p>
            <h2>Use these prompts live</h2>
            <div class="prompt-list">
              <button @click="statusMessage = 'Judge prompt: Which matters more for this payment: lowest fee, fastest settlement, or highest approval probability?'">
                Tradeoff question
              </button>
              <button @click="statusMessage = 'Judge prompt: Watch what happens when the preferred provider becomes unavailable.'">
                Outage tease
              </button>
              <button @click="statusMessage = 'Judge prompt: This is where duplicate-charge prevention and balance controls protect trust.'">
                Risk question
              </button>
            </div>

            <div class="mini-scoreboard">
              <div>
                <span>Health</span>
                <strong>{{ providerHealth }}%</strong>
              </div>
              <div>
                <span>Approval</span>
                <strong>{{ approvalRate }}%</strong>
              </div>
              <div>
                <span>Latency</span>
                <strong>{{ averageLatency }} ms</strong>
              </div>
            </div>
          </aside>
        </section>
      </template>

      <template v-else-if="activePanel === 'showcase'">
        <section class="layout">
          <div class="primary-column">
            <section class="panel decision-panel">
              <div class="panel-heading">
                <div>
                  <p class="eyebrow">AI decision</p>
                  <h2>Explainable corridor ranking</h2>
                </div>
                <span class="status-pill" :class="latestPayment?.status === 'SUCCEEDED' ? 'ok' : 'warn'">
                  {{ latestPayment?.status ?? 'READY' }}
                </span>
              </div>

              <div v-if="selectedCandidate" class="winner">
                <div>
                  <span>Winning path</span>
                  <strong>
                    {{ selectedCandidate.sourceProvider }} → {{ selectedCandidate.routeProvider }} →
                    {{ selectedCandidate.destinationProvider }}
                  </strong>
                </div>
                <div>
                  <span>Final score</span>
                  <strong>{{ Math.round(selectedCandidate.finalScore * 100) }}/100</strong>
                </div>
                <div>
                  <span>Success probability</span>
                  <strong>{{ formatPercent(selectedCandidate.successProbability) }}</strong>
                </div>
              </div>

              <div v-if="rankedCandidates.length" class="ranking">
                <div v-for="candidate in rankedCandidates" :key="`${candidate.sourceAccountId}-${candidate.routeProvider}-${candidate.destinationAccountId}`" class="rank-row" :class="{ selected: candidate.selected }">
                  <div class="rank-route">
                    <strong>{{ candidate.sourceProvider }} → {{ candidate.routeProvider }} → {{ candidate.destinationProvider }}</strong>
                    <span>{{ candidate.explanation }}</span>
                  </div>
                  <div class="score-bars">
                    <label>
                      Reliability
                      <span><i :style="{ width: `${Math.round(candidate.reliabilityScore * 100)}%` }"></i></span>
                    </label>
                    <label>
                      Cost
                      <span><i :style="{ width: `${Math.round(candidate.costScore * 100)}%` }"></i></span>
                    </label>
                    <label>
                      Latency
                      <span><i :style="{ width: `${Math.round(candidate.latencyScore * 100)}%` }"></i></span>
                    </label>
                  </div>
                  <strong class="rank-score">{{ Math.round(candidate.finalScore * 100) }}</strong>
                </div>
              </div>

              <div v-else class="empty-state">
                <strong>No transaction scored yet.</strong>
                <span>Load the judge demo and run Smart Route to see ranked corridors.</span>
              </div>
            </section>

            <section class="panel">
              <div class="panel-heading">
                <div>
                  <p class="eyebrow">Live operations</p>
                  <h2>Provider health and failover controls</h2>
                </div>
                <button class="small" :disabled="busy" @click="restoreProviderHealth">Restore all</button>
              </div>
              <div class="provider-grid">
                <article v-for="provider in providers" :key="provider.code" class="provider-card">
                  <div class="provider-topline">
                    <strong>{{ provider.name }}</strong>
                    <span class="mode-badge" :class="providerModeClass(provider.mode)">{{ provider.mode }}</span>
                  </div>
                  <div class="provider-metrics">
                    <span>Success {{ formatPercent(provider.successRate) }}</span>
                    <span>Health {{ formatPercent(provider.healthScore) }}</span>
                    <span>Cost {{ formatPercent(provider.costScore) }}</span>
                    <span>{{ provider.latencyMs }} ms</span>
                  </div>
                  <select :value="provider.mode" @change="setProviderMode(provider.code, ($event.target as HTMLSelectElement).value)">
                    <option>HEALTHY</option>
                    <option>SLOW</option>
                    <option>UNSTABLE</option>
                    <option>UNAVAILABLE</option>
                  </select>
                </article>
              </div>
            </section>
          </div>

          <aside class="side-column">
            <section class="panel">
              <p class="eyebrow">Business impact</p>
              <h2>What judges should notice</h2>
              <div class="impact-grid">
                <article v-for="card in impactCards" :key="card.label">
                  <span>{{ card.label }}</span>
                  <strong>{{ card.value }}</strong>
                  <small>{{ card.detail }}</small>
                </article>
              </div>
            </section>

            <section class="panel">
              <p class="eyebrow">Risk and compliance</p>
              <h2>Gateway safeguards</h2>
              <ul class="check-list">
                <li>Idempotency key blocks duplicate charges during retries.</li>
                <li>Verified linked accounts are required before routing.</li>
                <li>Available balance is checked and debited after success.</li>
                <li>Unavailable providers are excluded from candidate paths.</li>
              </ul>
            </section>

            <section class="panel payment-summary">
              <p class="eyebrow">Latest payment</p>
              <h2>{{ latestPayment?.merchantReference ?? 'No payment yet' }}</h2>
              <dl>
                <div>
                  <dt>Amount</dt>
                  <dd>{{ latestPayment ? `${formatMoney(latestPayment.amount)} ${latestPayment.currency}` : 'Pending' }}</dd>
                </div>
                <div>
                  <dt>Source</dt>
                  <dd>{{ accountName(latestPayment?.sourceAccountId ?? null) }}</dd>
                </div>
                <div>
                  <dt>Destination</dt>
                  <dd>{{ accountName(latestPayment?.destinationAccountId ?? null) }}</dd>
                </div>
                <div>
                  <dt>Attempts</dt>
                  <dd>{{ latestPayment?.attempts.length ?? 0 }}</dd>
                </div>
              </dl>
            </section>
          </aside>
        </section>
      </template>

      <template v-else>
        <section class="layout">
          <div class="primary-column">
            <section class="panel">
              <div class="panel-heading">
                <div>
                  <p class="eyebrow">Manual API demo</p>
                  <h2>Create sender or receiver</h2>
                </div>
              </div>
              <div class="form-grid">
                <input v-model="userForm.name" placeholder="Name" />
                <input v-model="userForm.phoneNumber" placeholder="Phone number" />
                <select v-model="userForm.role">
                  <option>INDIVIDUAL</option>
                  <option>MERCHANT</option>
                </select>
                <button @click="createUser">Create user</button>
              </div>
            </section>

            <section class="panel">
              <div class="panel-heading">
                <div>
                  <p class="eyebrow">Account linking</p>
                  <h2>Register payment rails</h2>
                </div>
              </div>
              <div class="form-grid">
                <select v-model="selectedAccountOwner">
                  <option value="">Choose account owner</option>
                  <option v-for="user in users" :key="user.id" :value="user.id">{{ user.name }} · {{ user.role }}</option>
                </select>
                <input v-model="accountForm.provider" placeholder="Provider, e.g. CBE" />
                <select v-model="accountForm.accountType">
                  <option>WALLET</option>
                  <option>BANK_ACCOUNT</option>
                  <option>CARD</option>
                  <option>MERCHANT_ACCOUNT</option>
                </select>
                <input v-model="accountForm.accountAlias" placeholder="Alias" />
                <input v-model="accountForm.accountReference" placeholder="Account number/token" />
                <input v-model.number="accountForm.availableBalance" type="number" placeholder="Balance" />
              </div>
              <div class="toggles">
                <label><input v-model="accountForm.smartPayEnabled" type="checkbox" /> Smart Pay eligible</label>
                <label><input v-model="accountForm.smartSettlementEnabled" type="checkbox" /> Smart Settlement eligible</label>
              </div>
              <button @click="linkAccount">Link account</button>
            </section>

            <section class="panel">
              <div class="panel-heading">
                <div>
                  <p class="eyebrow">Payment API</p>
                  <h2>Create smart payment</h2>
                </div>
              </div>
              <div class="form-grid">
                <select v-model="paymentForm.senderId" @change="refreshAccounts">
                  <option value="">Select sender</option>
                  <option v-for="user in senderUsers" :key="user.id" :value="user.id">{{ user.name }}</option>
                </select>
                <select v-model="paymentForm.receiverId" @change="refreshAccounts">
                  <option value="">Select receiver</option>
                  <option v-for="user in receiverUsers" :key="user.id" :value="user.id">{{ user.name }}</option>
                </select>
                <input v-model="paymentForm.merchantReference" placeholder="Reference" />
                <input v-model.number="paymentForm.amount" type="number" placeholder="Amount" />
              </div>
              <div class="toggles">
                <label><input v-model="paymentForm.smartPay" type="checkbox" /> Smart Pay</label>
                <label><input v-model="paymentForm.smartSettlement" type="checkbox" /> Smart Settlement</label>
              </div>
              <div class="form-grid">
                <select v-model="paymentForm.selectedSourceAccountId" :disabled="paymentForm.smartPay">
                  <option value="">Choose source account</option>
                  <option v-for="account in senderAccounts" :key="account.id" :value="account.id">
                    {{ account.provider }} · {{ account.accountAlias }} · {{ account.maskedReference }}
                  </option>
                </select>
                <select v-model="paymentForm.selectedDestinationAccountId" :disabled="paymentForm.smartSettlement">
                  <option value="">Choose destination account</option>
                  <option v-for="account in receiverAccounts" :key="account.id" :value="account.id">
                    {{ account.provider }} · {{ account.accountAlias }} · {{ account.maskedReference }}
                  </option>
                </select>
              </div>
              <button @click="createPayment()">Process payment</button>
            </section>
          </div>

          <aside class="side-column">
            <section class="panel">
              <p class="eyebrow">Recent payments</p>
              <h2>Transaction log</h2>
              <div class="payment-list">
                <article v-for="payment in payments.slice(0, 8)" :key="payment.id">
                  <strong>{{ payment.merchantReference }}</strong>
                  <span>{{ formatMoney(payment.amount) }} {{ payment.currency }}</span>
                  <span>{{ payment.routeProvider ?? 'No route' }}</span>
                  <em :class="payment.status === 'SUCCEEDED' ? 'ok-text' : 'bad-text'">{{ payment.status }}</em>
                </article>
              </div>
            </section>

            <section v-if="result" class="panel">
              <p class="eyebrow">Raw API response</p>
              <pre>{{ JSON.stringify(result, null, 2) }}</pre>
            </section>
          </aside>
        </section>
      </template>
    </main>
  </div>
</template>

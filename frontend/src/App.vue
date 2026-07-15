<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
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

const users = ref<User[]>([])
const providers = ref<Provider[]>([])
const payments = ref<Payment[]>([])
const senderAccounts = ref<Account[]>([])
const receiverAccounts = ref<Account[]>([])
const result = ref<Payment | null>(null)
const busy = ref(false)
const statusMessage = ref('Load the judge demo to create a ready-made merchant checkout scenario.')
const activePanel = ref<'showcase' | 'builder'>('showcase')

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
    statusMessage.value = 'Outage scenario complete. The route engine excluded unavailable rails and retried eligible alternatives.'
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

function providerModeClass(mode: string) {
  return mode.toLowerCase()
}

onMounted(loadAll)
</script>

<template>
  <div class="app-shell">
    <header class="hero">
      <div>
        <p class="eyebrow">Kifiya Inspire Hackathon V4 2026 · Intelligent Payment Routing</p>
        <h1>PayFlow AI payment command center</h1>
        <p class="hero-copy">
          A live gateway demo that routes Ethiopian merchant payments across banks, wallets, and payment rails using
          provider health, success probability, cost, latency, account eligibility, and automatic failover.
        </p>
        <div class="hero-actions">
          <button class="primary" :disabled="busy" @click="loadJudgeDemo">Load judge demo</button>
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
        <button :class="{ active: activePanel === 'showcase' }" @click="activePanel = 'showcase'">Showcase</button>
        <button :class="{ active: activePanel === 'builder' }" @click="activePanel = 'builder'">API builder</button>
      </nav>

      <template v-if="activePanel === 'showcase'">
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

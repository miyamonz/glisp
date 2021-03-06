<template>
	<div class="ViewExpTree">
		<div
			:class="{
				clickable: labelInfo.clickable,
				hidden: ui.hidden,
				active,
				selected,
				hovering,
			}"
			@click="labelInfo.clickable && onClick($event)"
			class="ViewExpTree__label"
		>
			<div
				:class="{expanded, expandable: labelInfo.expandable}"
				@click="labelInfo.expandable && toggleExpanded()"
				class="ViewExpTree__icon"
			>
				<i
					:class="labelInfo.icon.value"
					:style="labelInfo.icon.style"
					v-if="labelInfo.icon.type === 'fontawesome'"
				/>
				<span
					:style="labelInfo.icon.style"
					v-else-if="labelInfo.icon.type === 'text'"
				>{{ labelInfo.icon.value }}</span>
				<span
					:style="labelInfo.icon.style"
					class="serif"
					v-if="labelInfo.icon.type === 'serif'"
				>{{ labelInfo.icon.value }}</span>
			</div>
			{{ labelInfo.label }}
			<i
				:class="{active: editing}"
				@click="onClickEditButton"
				class="ViewExpTree__editing fas fa-code"
				v-if="labelInfo.editable"
			/>
		</div>
		<div class="ViewExpTree__children" v-if="labelInfo.children && expanded">
			<ViewExpTree
				:activeExp="activeExp"
				:editingExp="editingExp"
				:exp="child"
				:expSelection="expSelection"
				:hoveringExp="hoveringExp"
				:key="i"
				@select="$emit('select', $event)"
				@toggle-selection="$emit('toggle-selection', $event)"
				@update:editingExp="$emit('update:editingExp', $event)"
				@update:exp="onUpdateChildExp(i, $event)"
				v-for="(child, i) in labelInfo.children"
			/>
		</div>
	</div>
</template>

<script lang="ts">
import {defineComponent, computed} from '@vue/composition-api'
import {NonReactive, nonReactive} from '@/utils'
import {
	MalVal,
	isList,
	isVector,
	MalType,
	getType,
	symbolFor as S,
	keywordFor as K,
	MalMap,
	createList as L,
	MalNode,
	MalSeq,
	isSymbolFor,
	isMap,
	cloneExp,
} from '@/mal/types'
import {printExp} from '@/mal'
import {reconstructTree} from '@/mal/reader'

enum DisplayMode {
	Node = 'node',
	Elements = 'elements',
}

interface Props {
	exp: NonReactive<MalVal>
	expSelection: Set<MalNode>
	activeExp: NonReactive<MalNode> | null
	editingExp: NonReactive<MalVal> | null
	hoveringExp: NonReactive<MalVal> | null
	mode: DisplayMode
}

const IconTexts = {
	[MalType.Function]: {type: 'serif', value: 'f'},
	[MalType.Number]: {type: 'text', value: '#'},
	[MalType.String]: {
		type: 'fontawesome',
		value: 'fas fa-quote-right',
		style: 'transform: scale(0.6);',
	},
	[MalType.Symbol]: {type: 'serif', value: 'x'},
	[MalType.Keyword]: {type: 'fontawesome', value: 'fas fa-key'},
} as {[type: string]: {type: string; value: string; style?: string}}

const S_UI_ANNOTATE = S('ui-annotate')
const K_NAME = K('name')
const K_EXPANDED = K('expanded')
const K_HIDDEN = K('hidden')

export default defineComponent({
	name: 'ViewExpTree',
	props: {
		exp: {
			required: true,
		},
		expSelection: {
			required: true,
		},
		activeExp: {
			required: true,
		},
		editingExp: {
			required: true,
		},
		hoveringExp: {
			required: true,
		},
		mode: {
			default: DisplayMode.Node,
		},
	},
	setup(props: Props, context) {
		/**
		 * The flag whether the exp has UI annotaiton
		 */
		const hasAnnotation = computed(() => {
			const exp = props.exp.value
			return isList(exp) && isSymbolFor(exp[0], 'ui-annotate')
		})

		/**
		 * the body of expression withouht ui-annotate wrapping
		 */
		const expBody = computed(() => {
			const exp = props.exp.value
			if (hasAnnotation.value) {
				return nonReactive((exp as MalSeq)[2])
			} else {
				return props.exp
			}
		})

		/**
		 * UI Annotations
		 */
		const ui = computed(() => {
			const exp = props.exp.value
			if (hasAnnotation.value) {
				const info = (exp as MalSeq)[1] as MalMap
				return {
					name: info[K_NAME] || null,
					expanded: info[K_EXPANDED] || false,
					hidden: info[K_HIDDEN] || false,
				}
			}

			return {name: null, expanded: false, hidden: false}
		})

		const labelInfo = computed(() => {
			const exp = expBody.value.value

			if (isList(exp)) {
				return {
					label: exp[0] ? printExp(exp[0]) : '<empty>',
					clickable: props.mode === DisplayMode.Node,
					expandable: props.mode === DisplayMode.Node,
					editable: true,
					icon: {
						type: 'fontawesome',
						value: 'fas fa-caret-right',
					},
					children: exp.slice(1).map(e => nonReactive(e)),
				}
			} else if (isVector(exp)) {
				return {
					label: printExp(exp),
					clickable: true,
					expandable: false,
					editable: true,
					icon: {type: 'text', value: '[ ]'},
					children: null,
				}
			} else if (isMap(exp)) {
				return {
					label: printExp(exp),
					clickable: true,
					expandable: false,
					editable: true,
					icon: {type: 'fontawesome', value: 'far fa-map'},
					children: null,
				}
			} else {
				return {
					label: printExp(exp, false),
					clickable: false,
					expandable: false,
					editable: false,
					icon: IconTexts[getType(exp)] || {type: 'text', value: '・'},
					children: null,
				}
			}
		})

		const expanded = computed(() => {
			return props.mode !== DisplayMode.Node
				? true
				: labelInfo.value.expandable
				? ui.value.expanded
				: false
		})

		const active = computed(() => {
			return props.activeExp && expBody.value.value === props.activeExp.value
		})

		const selected = computed(() => {
			return props.expSelection.has(expBody.value.value as MalNode)
		})

		const hovering = computed(() => {
			return (
				props.hoveringExp && expBody.value.value === props.hoveringExp.value
			)
		})

		const editing = computed(() => {
			return props.editingExp && expBody.value.value === props.editingExp.value
		})

		/**
		 * Events
		 */
		function onClick(e: MouseEvent) {
			const ctrlPressed = e.ctrlKey || e.metaKey
			context.emit(ctrlPressed ? 'toggle-selection' : 'select', expBody.value)
		}

		function toggleExpanded() {
			const annotation = {} as {[k: string]: MalVal}
			if (!ui.value.expanded === true) {
				annotation[K_EXPANDED] = true
			}
			if (ui.value.name !== null) {
				annotation[K_NAME] = ui.value.name
			}

			const newExp = nonReactive(
				Object.keys(annotation).length > 0
					? L(S_UI_ANNOTATE, annotation, expBody.value.value)
					: expBody.value.value
			)

			context.emit('update:exp', newExp)
		}

		function onUpdateChildExp(i: number, replaced: NonReactive<MalNode>) {
			const newExpBody = cloneExp(expBody.value.value) as MalSeq

			;(newExpBody as MalSeq)[i + 1] = replaced.value

			let newExp

			if (hasAnnotation.value) {
				newExp = L(S_UI_ANNOTATE, (props.exp.value as MalSeq)[1], newExpBody)
			} else {
				newExp = newExpBody
			}

			reconstructTree(newExp)

			context.emit('update:exp', nonReactive(newExp))
		}

		function onClickEditButton(e: MouseEvent) {
			e.stopPropagation()
			context.emit('update:editingExp', expBody.value)
		}

		return {
			labelInfo,
			active,
			selected,
			hovering,
			editing,
			onClick,
			expanded,
			ui,
			toggleExpanded,
			onUpdateChildExp,
			onClickEditButton,
		}
	},
})
</script>

<style lang="stylus">
.ViewExpTree
	overflow hidden
	// padding-left 1rem
	width 100%
	user-select none

	&.destructed
		padding-left 0

		.ViewExpTree__children:before
			display none

	&__label
		position relative
		overflow hidden
		padding 0.6rem 0.5rem 0.6rem 0.3rem
		color var(--comment)
		text-overflow ellipsis
		white-space nowrap

		&:after
			position absolute
			top 0
			right 0
			left 0rem
			height 100%
			content ''
			opacity 0
			transition opacity 0.05s ease
			pointer-events none

		&:hover
			&:after
				opacity 0.15

		&.hidden
			text-decoration line-through

		&.clickable
			color var(--foreground)
			cursor pointer

			&:hover
				color var(--highlight)

			&:after
				border 1px solid var(--highlight)

		&.active
			background var(--input)
			color var(--highlight)
			font-weight bold

			&:after
				background var(--highlight)
				opacity 0.1

		&.selected
			color var(--highlight)

			&:after
				background var(--highlight)
				opacity 0.08

		&.hovering
			color var(--highlight)

			&:after
				border 1px solid var(--highlight)
				opacity 0.15

	&__icon
		display inline-block
		margin-right 0.2rem
		width 1rem
		color var(--comment)
		text-align center
		opacity 0.7
		input-transition()

		&.expandable:hover
			color var(--highlight)
			opacity 1

		&.expanded
			transform rotate(90deg)

		.serif
			font-weight bold
			font-style italic
			font-family 'EB Garamond', serif
			line-height 1rem

	&__editing
		position absolute
		right 1rem
		color var(--comment)
		opacity 0
		cursor pointer

		&:hover
			opacity 0.5

		&.active
			opacity 0.7

	&__children
		position relative
		padding-left 1rem

		&:before
			position absolute
			top 0
			left 0.8rem
			width 0
			height 100%
			border-left 1px dotted var(--border)
			content ''
</style>

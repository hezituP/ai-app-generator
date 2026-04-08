import { nextTick, onBeforeUnmount, type Ref, ref, watch } from 'vue'

const HOST_MESSAGE_SOURCE = 'he-visual-editor-host'
const IFRAME_MESSAGE_SOURCE = 'he-visual-editor-iframe'

export interface VisualEditorSelection {
  tagName: string
  selector: string
  text: string
  id?: string
  className?: string
}

interface UseVisualEditorOptions {
  iframeRef: Ref<HTMLIFrameElement | null>
  enabledRef: Ref<boolean>
}

function getInjectScript() {
  return `
    (() => {
      if (window.__HE_VISUAL_EDITOR__) {
        return
      }

      const state = {
        enabled: false,
        hoverEl: null,
        selectedEl: null,
      }

      const HOVER_ATTR = 'data-he-visual-hover'
      const SELECTED_ATTR = 'data-he-visual-selected'
      const IGNORE_ATTR = 'data-he-visual-ignore'

      const style = document.createElement('style')
      style.setAttribute(IGNORE_ATTR, 'true')
      style.textContent = \`
        [\${HOVER_ATTR}] {
          outline: 2px solid rgba(59, 130, 246, 0.9) !important;
          outline-offset: 2px !important;
          cursor: pointer !important;
        }
        [\${SELECTED_ATTR}] {
          outline: 3px solid rgba(37, 99, 235, 1) !important;
          outline-offset: 2px !important;
          box-shadow: 0 0 0 2px rgba(191, 219, 254, 0.9) !important;
        }
      \`
      document.head.appendChild(style)

      function normalizeText(value) {
        return (value || '').replace(/\\s+/g, ' ').trim().slice(0, 120)
      }

      function buildSelector(element) {
        if (!element || element.nodeType !== 1) {
          return ''
        }
        if (element.id) {
          return '#' + element.id
        }
        const parts = []
        let current = element
        while (current && current.nodeType === 1 && current.tagName !== 'HTML') {
          let selector = current.tagName.toLowerCase()
          if (current.classList && current.classList.length) {
            selector += '.' + Array.from(current.classList).slice(0, 2).join('.')
          } else if (current.parentElement) {
            const siblings = Array.from(current.parentElement.children).filter((item) => item.tagName === current.tagName)
            if (siblings.length > 1) {
              selector += ':nth-of-type(' + (siblings.indexOf(current) + 1) + ')'
            }
          }
          parts.unshift(selector)
          if (parts.length >= 4) {
            break
          }
          current = current.parentElement
        }
        return parts.join(' > ')
      }

      function buildPayload(element) {
        return {
          tagName: element.tagName.toLowerCase(),
          selector: buildSelector(element),
          text: normalizeText(element.innerText || element.textContent || ''),
          id: element.id || '',
          className: normalizeText(element.className || ''),
        }
      }

      function clearHover() {
        if (state.hoverEl && state.hoverEl !== state.selectedEl) {
          state.hoverEl.removeAttribute(HOVER_ATTR)
        }
        state.hoverEl = null
      }

      function clearSelection(shouldNotify = true) {
        if (state.selectedEl) {
          state.selectedEl.removeAttribute(SELECTED_ATTR)
          state.selectedEl = null
        }
        clearHover()
        if (shouldNotify) {
          window.parent.postMessage({
            source: '${IFRAME_MESSAGE_SOURCE}',
            type: 'clear-selection',
          }, window.location.origin)
        }
      }

      function shouldIgnore(element) {
        return !element
          || element.tagName === 'HTML'
          || element.tagName === 'BODY'
          || element.hasAttribute(IGNORE_ATTR)
          || element.closest('script, style, link, meta')
      }

      function setHover(element) {
        if (!state.enabled || shouldIgnore(element)) {
          clearHover()
          return
        }
        if (state.hoverEl && state.hoverEl !== state.selectedEl) {
          state.hoverEl.removeAttribute(HOVER_ATTR)
        }
        state.hoverEl = element
        if (state.hoverEl !== state.selectedEl) {
          state.hoverEl.setAttribute(HOVER_ATTR, 'true')
        }
      }

      function setSelected(element) {
        if (shouldIgnore(element)) {
          return
        }
        if (state.selectedEl) {
          state.selectedEl.removeAttribute(SELECTED_ATTR)
        }
        if (state.hoverEl && state.hoverEl !== element) {
          state.hoverEl.removeAttribute(HOVER_ATTR)
        }
        state.selectedEl = element
        state.selectedEl.setAttribute(SELECTED_ATTR, 'true')
        window.parent.postMessage({
          source: '${IFRAME_MESSAGE_SOURCE}',
          type: 'select-element',
          payload: buildPayload(element),
        }, window.location.origin)
      }

      function onMouseMove(event) {
        if (!state.enabled) {
          return
        }
        setHover(event.target.closest('*'))
      }

      function onMouseLeave() {
        if (!state.enabled) {
          return
        }
        clearHover()
      }

      function onClick(event) {
        if (!state.enabled) {
          return
        }
        const target = event.target.closest('*')
        if (shouldIgnore(target)) {
          return
        }
        event.preventDefault()
        event.stopPropagation()
        setSelected(target)
      }

      document.addEventListener('mousemove', onMouseMove, true)
      document.addEventListener('mouseleave', onMouseLeave, true)
      document.addEventListener('click', onClick, true)

      window.addEventListener('message', (event) => {
        if (event.origin !== window.location.origin || !event.data || event.data.source !== '${HOST_MESSAGE_SOURCE}') {
          return
        }
        if (event.data.type === 'set-enabled') {
          state.enabled = Boolean(event.data.payload)
          if (!state.enabled) {
            clearHover()
          }
        }
        if (event.data.type === 'clear-selection') {
          clearSelection(false)
        }
      })

      window.__HE_VISUAL_EDITOR__ = true
    })();
  `
}

export function useVisualEditor({ iframeRef, enabledRef }: UseVisualEditorOptions) {
  const selectedElement = ref<VisualEditorSelection | null>(null)

  function postToIframe(type: string, payload?: unknown) {
    const iframeWindow = iframeRef.value?.contentWindow
    if (!iframeWindow) {
      return
    }
    iframeWindow.postMessage(
      {
        source: HOST_MESSAGE_SOURCE,
        type,
        payload,
      },
      window.location.origin,
    )
  }

  function injectBridge() {
    const iframe = iframeRef.value
    if (!iframe) {
      return
    }
    const doc = iframe.contentDocument
    if (!doc || !doc.head) {
      return
    }
    if (doc.querySelector('script[data-he-visual-editor-bridge="true"]')) {
      postToIframe('set-enabled', enabledRef.value)
      return
    }
    const script = doc.createElement('script')
    script.type = 'text/javascript'
    script.dataset.heVisualEditorBridge = 'true'
    script.text = getInjectScript()
    doc.head.appendChild(script)
    postToIframe('set-enabled', enabledRef.value)
  }

  function handleFrameLoad() {
    injectBridge()
  }

  function handleWindowMessage(event: MessageEvent) {
    if (event.origin !== window.location.origin || !event.data || event.data.source !== IFRAME_MESSAGE_SOURCE) {
      return
    }
    if (event.data.type === 'select-element') {
      selectedElement.value = event.data.payload as VisualEditorSelection
      return
    }
    if (event.data.type === 'clear-selection') {
      selectedElement.value = null
    }
  }

  function clearSelectedElement() {
    selectedElement.value = null
    postToIframe('clear-selection')
  }

  watch(
    iframeRef,
    async (iframe, previousIframe) => {
      if (previousIframe) {
        previousIframe.removeEventListener('load', handleFrameLoad)
      }
      if (!iframe) {
        return
      }
      iframe.addEventListener('load', handleFrameLoad)
      await nextTick()
      if (iframe.contentDocument?.readyState === 'complete') {
        injectBridge()
      }
    },
    { immediate: true },
  )

  watch(
    enabledRef,
    (enabled) => {
      postToIframe('set-enabled', enabled)
      if (!enabled) {
        postToIframe('clear-selection')
      }
    },
    { immediate: true },
  )

  window.addEventListener('message', handleWindowMessage)

  onBeforeUnmount(() => {
    iframeRef.value?.removeEventListener('load', handleFrameLoad)
    window.removeEventListener('message', handleWindowMessage)
  })

  return {
    selectedElement,
    clearSelectedElement,
    syncVisualEditor: injectBridge,
  }
}

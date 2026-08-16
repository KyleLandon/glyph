(() => {
  const joinBtn = document.getElementById("join-btn");
  const modal = document.getElementById("join-modal");
  const toast = document.getElementById("toast");
  if (!joinBtn || !modal || !toast) return;

  let hideTimer;
  let lastFocus = null;

  function showToast(message) {
    if (message) toast.textContent = message;
    toast.hidden = false;
    requestAnimationFrame(() => toast.classList.add("is-visible"));
    clearTimeout(hideTimer);
    hideTimer = setTimeout(() => {
      toast.classList.remove("is-visible");
      setTimeout(() => {
        toast.hidden = true;
      }, 350);
    }, 2200);
  }

  async function copyAddress(address, button) {
    try {
      await navigator.clipboard.writeText(address);
    } catch {
      const field = document.createElement("textarea");
      field.value = address;
      field.setAttribute("readonly", "");
      field.style.position = "fixed";
      field.style.opacity = "0";
      document.body.appendChild(field);
      field.select();
      document.execCommand("copy");
      field.remove();
    }
    if (button) {
      const previous = button.textContent;
      button.textContent = "Copied";
      button.classList.add("is-copied");
      setTimeout(() => {
        button.textContent = previous;
        button.classList.remove("is-copied");
      }, 1800);
    }
    showToast(address + " copied — paste in Minecraft");
  }

  document.querySelectorAll("[data-copy]").forEach((el) => {
    el.addEventListener("click", (event) => {
      event.preventDefault();
      const address = el.getAttribute("data-copy");
      if (!address) return;
      const button = el.tagName === "BUTTON" ? el : null;
      copyAddress(address, button);
    });
    if (el.tagName !== "BUTTON") {
      el.title = "Click to copy";
    }
  });

  function openModal() {
    lastFocus = document.activeElement;
    modal.hidden = false;
    modal.removeAttribute("hidden");
    modal.setAttribute("aria-hidden", "false");
    document.body.classList.add("modal-open");
    void modal.offsetWidth;
    modal.classList.add("is-open");
    const closeBtn = modal.querySelector(".modal-close");
    if (closeBtn) closeBtn.focus({ preventScroll: true });
  }

  function closeModal() {
    modal.classList.remove("is-open");
    modal.setAttribute("aria-hidden", "true");
    document.body.classList.remove("modal-open");
    setTimeout(() => {
      modal.hidden = true;
      modal.setAttribute("hidden", "");
      if (lastFocus && typeof lastFocus.focus === "function") lastFocus.focus();
    }, 220);
  }

  joinBtn.addEventListener("click", (event) => {
    event.preventDefault();
    openModal();
  });

  modal.querySelectorAll("[data-close]").forEach((el) => {
    el.addEventListener("click", closeModal);
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && modal.classList.contains("is-open")) {
      closeModal();
    }
  });
})();

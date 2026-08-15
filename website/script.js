(() => {
  const joinBtn = document.getElementById("join-btn");
  const modal = document.getElementById("join-modal");
  const copyBtn = document.getElementById("copy-btn");
  const toast = document.getElementById("toast");
  const addressEl = document.getElementById("server-address");
  if (!joinBtn || !modal || !copyBtn || !toast) return;

  const address =
    joinBtn.dataset.address ||
    (addressEl && addressEl.textContent.trim()) ||
    "play.glyphmc.net";

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

  async function copyAddress() {
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
    copyBtn.textContent = "Copied";
    copyBtn.classList.add("is-copied");
    showToast("Address copied — paste in Minecraft");
    setTimeout(() => {
      copyBtn.textContent = "Copy";
      copyBtn.classList.remove("is-copied");
    }, 1800);
  }

  function openModal() {
    lastFocus = document.activeElement;
    // Class-based open (more reliable than the HTML hidden attribute alone).
    modal.hidden = false;
    modal.removeAttribute("hidden");
    modal.setAttribute("aria-hidden", "false");
    document.body.classList.add("modal-open");
    // Force layout, then animate in.
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

  // Join opens the how-to dialog — it does NOT copy the address.
  joinBtn.addEventListener("click", (event) => {
    event.preventDefault();
    openModal();
  });
  copyBtn.addEventListener("click", (event) => {
    event.preventDefault();
    copyAddress();
  });

  if (addressEl) {
    addressEl.addEventListener("click", () => copyAddress());
    addressEl.title = "Click to copy";
  }

  modal.querySelectorAll("[data-close]").forEach((el) => {
    el.addEventListener("click", closeModal);
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && modal.classList.contains("is-open")) {
      closeModal();
    }
  });
})();

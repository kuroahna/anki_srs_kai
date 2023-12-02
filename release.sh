#!/bin/bash

if [ -z "$1" ] || [ -z "$2" ] || [ -z "$3" ]; then
    echo "usage: $0 PATH_TO_INPUT_WASM_FILE PATH_TO_INPUT_JS_FILE PATH_TO_OUTPUT_JS_FILE"
    exit 1
fi

set -v

echo "
const deckOptions = {
    \"deck1\": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 3,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: true,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 0.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return currentEaseFactor / Math.pow(currentInterval, 0.054297);
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 0.0;
            },
        },
    },
    \"Global Settings\": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 3,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: true,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            // Approximation of the default FSRS v4 parameters
            // [0.4, 0.6, 2.4, 5.8, 4.93, 0.94, 0.86, 0.01, 1.49, 0.14, 0.94, 2.18, 0.05, 0.34, 1.26, 0.29, 2.61]
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return (currentEaseFactor / Math.pow(currentInterval, 0.028372)) - 0.739;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return (currentEaseFactor / Math.pow(currentInterval, 0.153776)) + 1.124;
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return (currentEaseFactor / Math.pow(currentInterval, 0.45)) + 5.348;
            },
        },
    },
};
" | cat - "$2" > "$3"

WASM_BYTES=$(hexdump -v -e '/1 "%u\n"' "$1" | paste -s -d "," -)
echo "
const wasmBytes = new Uint8Array([${WASM_BYTES}]);
const response = new Response(
    wasmBytes,
    {
        headers: {
            'Content-Type': 'application/wasm'
        }
    }
);

try {
    await wasm_bindgen(response);

    wasm_bindgen.calculateNextCardStates(deckOptions, states);
} catch (e) {
    function openCustomModal(message) {
        const modalContainer = document.createElement('div');
        modalContainer.id = 'custom-modal';

        const modalMessage = document.createElement('p');
        modalMessage.id = 'modal-message';
        modalMessage.innerText = message;
        modalMessage.style.fontFamily = 'Arial';

        const okButton = document.createElement('button');
        okButton.innerText = 'OK';
        okButton.onclick = closeCustomModal;

        modalContainer.appendChild(modalMessage);
        modalContainer.appendChild(okButton);

        modalContainer.style.display = 'none';
        modalContainer.style.position = 'fixed';
        modalContainer.style.top = '50%';
        modalContainer.style.left = '50%';
        modalContainer.style.transform = 'translate(-50%, -50%)';
        modalContainer.style.padding = '20px';
        modalContainer.style.backgroundColor = '#fff';
        modalContainer.style.border = '1px solid #ccc';
        modalContainer.style.boxShadow = '0 2px 10px rgba(0, 0, 0, 0.1)';
        modalContainer.style.zIndex = '1000';

        modalMessage.style.marginBottom = '15px';
        modalMessage.style.color = '#000';

        okButton.style.cursor = 'pointer';
        okButton.style.padding = '8px 16px';
        okButton.style.backgroundColor = '#007BFF';
        okButton.style.color = '#fff';
        okButton.style.border = 'none';
        okButton.style.borderRadius = '4px';

        document.body.appendChild(modalContainer);

        modalContainer.style.display = 'block';
    }

    function closeCustomModal() {
        const modalContainer = document.getElementById('custom-modal');
        document.body.removeChild(modalContainer);
    }

    openCustomModal(e.message);
}
" >> "$3"

#!/usr/bin/env bash
# Pull the stack-generated SSH private key from SSM Parameter Store.
# Usage: ./fetch-keypair.sh [stack-name] [output-path]
set -euo pipefail

REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-eu-west-2}}"
STACK="${1:-empathy-paas}"
OUT="${2:-${HOME}/.config/base-docker/empathy-paas.pem}"

KEY_ID="$(
  aws cloudformation describe-stacks \
    --region "${REGION}" \
    --stack-name "${STACK}" \
    --query 'Stacks[0].Outputs[?OutputKey==`KeyPairId`].OutputValue' \
    --output text
)"

if [[ -z "${KEY_ID}" || "${KEY_ID}" == "None" ]]; then
  echo "No KeyPairId output on stack ${STACK} in ${REGION}" >&2
  exit 1
fi

PARAM="/ec2/keypair/${KEY_ID}"
mkdir -p "$(dirname "${OUT}")"
umask 077
aws ssm get-parameter \
  --region "${REGION}" \
  --name "${PARAM}" \
  --with-decryption \
  --query Parameter.Value \
  --output text > "${OUT}"
chmod 600 "${OUT}"

echo "Wrote ${OUT} (${PARAM})"
echo "Use the same PEM for secrets_vals.pem.proxy and secrets_vals.pem.test"
